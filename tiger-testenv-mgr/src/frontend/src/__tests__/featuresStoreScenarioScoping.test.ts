///
///
/// Copyright 2021-2026 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, test } from "vitest";
import { useFeaturesStore } from "../stores/features";
import FeatureUpdate from "../types/testsuite/FeatureUpdate";
import ScenarioUpdate from "../types/testsuite/ScenarioUpdate";
import StepUpdate, { type IStep } from "../types/testsuite/StepUpdate";
import TestResult from "../types/testsuite/TestResult";
import type MessageMetaDataDto from "../types/rbel/MessageMetaDataDto";

function message(timestampMs: number): MessageMetaDataDto {
  return {
    uuid: `uuid-${timestampMs}`,
    timestamp: new Date(timestampMs).toISOString(),
    sequenceNumber: timestampMs,
  } as unknown as MessageMetaDataDto;
}

function step(messages: MessageMetaDataDto[], subSteps: IStep[] = []): StepUpdate {
  const newStep = new StepUpdate();
  newStep.rbelMetaData = messages;
  newStep.subSteps = subSteps;
  return newStep;
}

function scenario(
  uniqueId: string,
  status: TestResult,
  steps: StepUpdate[] = [],
): ScenarioUpdate {
  const newScenario = new ScenarioUpdate();
  newScenario.uniqueId = uniqueId;
  newScenario.description = uniqueId;
  newScenario.status = status;
  steps.forEach((entry, index) => newScenario.steps.set(String(index), entry));
  return newScenario;
}

/** Puts all scenarios into a single feature, preserving their order. */
function givenScenarios(scenarios: ScenarioUpdate[]) {
  const store = useFeaturesStore();
  const feature = new FeatureUpdate();
  feature.description = "a feature";
  scenarios.forEach((entry) => feature.scenarios.set(entry.uniqueId, entry));
  store.featureUpdateMap.set("a feature", feature);
  return store;
}

describe("features store - scenario scoping for the Rbel log (TGR-2300)", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  describe("resolving which scenario to scope to", () => {
    test("resolves to nothing while no scenario has run", () => {
      const store = givenScenarios([
        scenario("discovered", TestResult.TEST_DISCOVERED, [step([message(1000)])]),
        scenario("unused", TestResult.UNUSED, [step([message(2000)])]),
      ]);

      expect(store.scopedScenario).toBeUndefined();
      expect(store.scopedScenarioTimeRange).toBeUndefined();
    });

    test("falls back to the last executed scenario, not the last declared one", () => {
      // Tiger's dry-run discovery pass pre-populates every scenario as TEST_DISCOVERED before
      // execution starts, so the last entry in iteration order is generally NOT the one that ran.
      const store = givenScenarios([
        scenario("ran-first", TestResult.PASSED, [step([message(1000)])]),
        scenario("ran-last", TestResult.FAILED, [step([message(2000)])]),
        scenario("never-ran", TestResult.TEST_DISCOVERED, [step([message(3000)])]),
      ]);

      expect(store.scopedScenario?.uniqueId).toBe("ran-last");
    });

    test("resolves to the running scenario while it is still executing", () => {
      const store = givenScenarios([
        scenario("done", TestResult.PASSED, [step([message(1000)])]),
        scenario("running", TestResult.EXECUTING, [step([message(2000)])]),
        scenario("pending", TestResult.TEST_DISCOVERED),
      ]);

      expect(store.scopedScenario?.uniqueId).toBe("running");
    });

    test("an explicitly selected scenario wins over the last-executed fallback", () => {
      const store = givenScenarios([
        scenario("pinned", TestResult.PASSED, [step([message(1000)])]),
        scenario("ran-last", TestResult.PASSED, [step([message(2000)])]),
      ]);

      store.toggleScenarioSelection("pinned");

      expect(store.scopedScenario?.uniqueId).toBe("pinned");
    });

    test("falls back again once the selection is toggled off", () => {
      const store = givenScenarios([
        scenario("pinned", TestResult.PASSED, [step([message(1000)])]),
        scenario("ran-last", TestResult.PASSED, [step([message(2000)])]),
      ]);

      store.toggleScenarioSelection("pinned");
      expect(store.isScenarioSelected("pinned")).toBe(true);

      store.toggleScenarioSelection("pinned");

      expect(store.isScenarioSelected("pinned")).toBe(false);
      expect(store.scopedScenario?.uniqueId).toBe("ran-last");
    });

    test("selecting another scenario replaces the previous selection", () => {
      const store = givenScenarios([
        scenario("first", TestResult.PASSED, [step([message(1000)])]),
        scenario("second", TestResult.PASSED, [step([message(2000)])]),
      ]);

      store.toggleScenarioSelection("first");
      store.toggleScenarioSelection("second");

      expect(store.isScenarioSelected("first")).toBe(false);
      expect(store.scopedScenario?.uniqueId).toBe("second");
    });

    test("a new test run clears a selection left over from the previous one", () => {
      const store = givenScenarios([scenario("pinned", TestResult.PASSED)]);
      store.toggleScenarioSelection("pinned");

      store.replaceFeatureMap({});

      expect(store.selectedScenarioId).toBeNull();
    });
  });

  describe("the time range the log is filtered by", () => {
    test("spans the scenario's own messages when nothing runs after it", () => {
      const store = givenScenarios([
        scenario("only", TestResult.PASSED, [step([message(1000), message(3000)])]),
      ]);

      expect(store.scopedScenarioTimeRange).toEqual({
        min: new Date(1000).toISOString(),
        max: new Date(3000).toISOString(),
      });
    });

    test("extends past the scenario's own last message up to the next scenario's first", () => {
      // A response can arrive after the scenario's bookkeeping already claimed its messages but
      // before the next scenario issues a request. Nothing else was running in that gap, so those
      // stragglers belong to the selected scenario and must stay inside the range.
      const store = givenScenarios([
        scenario("selected", TestResult.PASSED, [step([message(1000), message(2000)])]),
        scenario("next", TestResult.PASSED, [step([message(5000)])]),
      ]);
      store.toggleScenarioSelection("selected");

      expect(store.scopedScenarioTimeRange).toEqual({
        min: new Date(1000).toISOString(),
        max: new Date(4999).toISOString(),
      });
    });

    test("skips over later scenarios that recorded no messages at all", () => {
      const store = givenScenarios([
        scenario("selected", TestResult.PASSED, [step([message(1000)])]),
        scenario("no-traffic", TestResult.PASSED, [step([])]),
        scenario("next-with-traffic", TestResult.PASSED, [step([message(8000)])]),
      ]);
      store.toggleScenarioSelection("selected");

      expect(store.scopedScenarioTimeRange?.max).toBe(new Date(7999).toISOString());
    });

    test("includes messages recorded by nested sub-steps", () => {
      const subStep = step([message(4000)]);
      const store = givenScenarios([
        scenario("with-substeps", TestResult.PASSED, [step([message(1000)], [subStep])]),
      ]);

      expect(store.scopedScenarioTimeRange?.max).toBe(new Date(4000).toISOString());
    });

    test("is undefined for a scenario that recorded no messages - the empty state", () => {
      // drives the "No Rbel messages recorded for ..." panel instead of silently falling back to
      // showing the unfiltered log.
      const store = givenScenarios([
        scenario("banner-only", TestResult.PASSED, [step([])]),
      ]);

      expect(store.scopedScenario?.uniqueId).toBe("banner-only");
      expect(store.scopedScenarioTimeRange).toBeUndefined();
    });
  });

  describe("scopedRbelMetadata - the shared filter behind the log, the sequence diagram and the topology", () => {
    test("returns every message while scoping is off", () => {
      const store = givenScenarios([
        scenario("first", TestResult.PASSED, [step([message(1000)])]),
        scenario("second", TestResult.PASSED, [step([message(2000)])]),
      ]);

      expect(store.scopedRbelMetadata.map((m) => m.uuid)).toEqual([
        "uuid-1000",
        "uuid-2000",
      ]);
    });

    test("narrows down to the scoped scenario's own messages once active", () => {
      const store = givenScenarios([
        scenario("first", TestResult.PASSED, [step([message(1000)])]),
        scenario("second", TestResult.PASSED, [step([message(2000)])]),
      ]);
      store.toggleScenarioSelection("first");
      store.toggleScoping();

      expect(store.scopedRbelMetadata.map((m) => m.uuid)).toEqual(["uuid-1000"]);
    });

    test("is empty once active for a scenario that recorded no messages", () => {
      const store = givenScenarios([
        scenario("banner-only", TestResult.PASSED, [step([])]),
      ]);
      store.toggleScoping();

      expect(store.scopedRbelMetadata).toEqual([]);
    });

    test("toggleScoping flips scopingActive back and forth", () => {
      const store = givenScenarios([scenario("only", TestResult.PASSED, [step([message(1000)])])]);

      expect(store.scopingActive).toBe(false);
      store.toggleScoping();
      expect(store.scopingActive).toBe(true);
      store.toggleScoping();
      expect(store.scopingActive).toBe(false);
    });
  });
});
