///
///
/// Copyright 2021-2025 gematik GmbH
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

import { defineStore } from "pinia";
import { computed, type Ref, ref } from "vue";
import FeatureUpdate, { type IJsonFeatures } from "@/types/testsuite/FeatureUpdate.ts";
import debug from "@/logging/log.ts";
import type ScenarioUpdate from "@/types/testsuite/ScenarioUpdate.ts";
import type { IStep } from "@/types/testsuite/StepUpdate.ts";
import type MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto.ts";
import TestResult from "@/types/testsuite/TestResult.ts";

function collectRbelMetaDataFromStep(step: IStep): MessageMetaDataDto[] {
  return [
    ...step.rbelMetaData,
    ...step.subSteps.flatMap(collectRbelMetaDataFromStep),
  ];
}

function scenarioMessageTimestamps(scenario: ScenarioUpdate): number[] {
  return Array.from(scenario.steps.values())
    .flatMap(collectRbelMetaDataFromStep)
    .map((metaData) => new Date(metaData.timestamp).getTime());
}

interface ScopedScenarioContext {
  scenario: ScenarioUpdate;
  /** index of `scenario` within `allScenarios`, so the time-range computation can look ahead to
   * whichever scenario runs next. */
  index: number;
  allScenarios: ScenarioUpdate[];
}

export const useFeaturesStore = defineStore("features", () => {
  const featureUpdateMap = ref(new Map<string, FeatureUpdate>()) as Ref<
    Map<string, FeatureUpdate>
  >;

  const rbelMetadata = computed(() => {
    const stepRbelMetaDataList: MessageMetaDataDto[] = [];

    for (const [, feature] of featureUpdateMap.value) {
      for (const [, scenario] of feature.scenarios) {
        for (const [, step] of scenario.steps) {
          for (const rbelMeta of step.rbelMetaData) {
            const rbelMetaSequenceNumber = rbelMeta.sequenceNumber;
            stepRbelMetaDataList.push({
              ...rbelMeta,
              sequenceNumber: rbelMetaSequenceNumber,
            });
          }
        }
      }
    }
    return stepRbelMetaDataList.toSorted(
      (a, b) =>
        new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
    );
  });

  /** uniqueId of the scenario explicitly pinned via {@link toggleScenarioSelection}, or null when
   * none is pinned (in which case scoping falls back to the last-executed scenario). */
  const selectedScenarioId: Ref<string | null> = ref(null);

  function toggleScenarioSelection(uniqueId: string) {
    selectedScenarioId.value =
      selectedScenarioId.value === uniqueId ? null : uniqueId;
  }

  function isScenarioSelected(uniqueId: string): boolean {
    return selectedScenarioId.value === uniqueId;
  }

  /**
   * The scenario the Rbel log view should scope to when "selected/last scenario only" is active,
   * together with its position among all scenarios (needed to find what runs next, for the
   * time-range upper bound below).
   *
   * Prefers the explicitly selected scenario (see {@link selectedScenarioId}); falls back to the
   * last scenario that actually started executing, determined by which executed scenario has the
   * most recent message timestamp (to handle replay scenarios that may run out of discovery order).
   */
  const scopedScenarioContext = computed<ScopedScenarioContext | undefined>(
    () => {
      const scenarios: ScenarioUpdate[] = [];
      for (const feature of featureUpdateMap.value.values()) {
        for (const scenario of feature.scenarios.values()) {
          scenarios.push(scenario);
        }
      }

      if (selectedScenarioId.value) {
        const index = scenarios.findIndex(
          (scenario) => scenario.uniqueId === selectedScenarioId.value,
        );
        if (index !== -1) {
          return { scenario: scenarios[index], index, allScenarios: scenarios };
        }
      }

      let lastExecutedScenario: ScenarioUpdate | undefined;
      let lastExecutedIndex: number = -1;
      let latestMessageTime: number = -1;

      for (let i = 0; i < scenarios.length; i++) {
        const scenario = scenarios[i];
        if (
          scenario.status !== TestResult.TEST_DISCOVERED &&
          scenario.status !== TestResult.UNUSED
        ) {
          const timestamps = scenarioMessageTimestamps(scenario);
          if (timestamps.length > 0) {
            const maxTime = Math.max(...timestamps);
            if (maxTime > latestMessageTime) {
              latestMessageTime = maxTime;
              lastExecutedScenario = scenario;
              lastExecutedIndex = i;
            }
          } else if (latestMessageTime === -1) {
            // no messages found yet among any scenario - remember this one as fallback
            lastExecutedScenario = scenario;
            lastExecutedIndex = i;
          }
        }
      }

      if (lastExecutedScenario !== undefined) {
        return {
          scenario: lastExecutedScenario,
          index: lastExecutedIndex,
          allScenarios: scenarios,
        };
      }
      return undefined;
    },
  );

  const scopedScenario = computed<ScenarioUpdate | undefined>(
    () => scopedScenarioContext.value?.scenario,
  );

  /**
   * The transmission-time range (ISO-8601) covering all Rbel messages that should be considered
   * part of {@link scopedScenario}.
   *
   * Uses timestamps rather than sequence numbers: a message downloaded via catch-up from a
   * remote/mesh proxy is recorded (and gets its sequence number assigned) when the download
   * reaches it, not in original transmission order - so sequence numbers are not reliably
   * contiguous for a scenario's messages. The actual transmission timestamp is.
   *
   * The upper bound is extended past the scenario's own last attributed message, up to (but not
   * including) the earliest attributed message of whichever later scenario has messages first.
   * Otherwise a straggler response that completes after the scenario's own bookkeeping already
   * claimed its messages - but before the next scenario made any request of its own - would fall
   * outside the range and look like it never happened, even though nothing else was running at
   * that point in time. This relies on sequential execution (already assumed elsewhere here): no
   * earlier scenario's traffic can complete after a later scenario has already started making
   * requests.
   */
  const scopedScenarioTimeRange = computed<
    { min: string; max: string } | undefined
  >(() => {
    const context = scopedScenarioContext.value;
    if (!context) {
      return undefined;
    }
    const ownTimestamps = scenarioMessageTimestamps(context.scenario);
    if (ownTimestamps.length === 0) {
      return undefined;
    }
    const min = Math.min(...ownTimestamps);
    let max = Math.max(...ownTimestamps);

    for (let i = context.index + 1; i < context.allScenarios.length; i++) {
      const nextTimestamps = scenarioMessageTimestamps(context.allScenarios[i]);
      if (nextTimestamps.length > 0) {
        max = Math.max(max, Math.min(...nextTimestamps) - 1);
        break;
      }
    }

    return {
      min: new Date(min).toISOString(),
      max: new Date(max).toISOString(),
    };
  });

  /** whether the scoping applies across all views (Rbel log, traffic visualization) that consume
   * {@link scopedRbelMetadata}, or everything is shown regardless of {@link scopedScenario}. Not
   * consumed by the topology diagram - see the comment on TopologyTab.vue's additionalEdges. */
  const scopingActive: Ref<boolean> = ref(false);

  function toggleScoping() {
    scopingActive.value = !scopingActive.value;
  }

  /**
   * {@link rbelMetadata}, narrowed down to {@link scopedScenarioTimeRange} while
   * {@link scopingActive} is on - the single definition of "this scenario's traffic" shared by
   * every view that can be scoped, so the Rbel log and the sequence diagram always agree on what
   * counts.
   */
  const scopedRbelMetadata = computed<MessageMetaDataDto[]>(() => {
    if (!scopingActive.value) {
      return rbelMetadata.value;
    }
    const range = scopedScenarioTimeRange.value;
    if (!range) {
      return [];
    }
    const min = new Date(range.min).getTime();
    const max = new Date(range.max).getTime();
    return rbelMetadata.value.filter((metaData) => {
      const t = new Date(metaData.timestamp).getTime();
      return t >= min && t <= max;
    });
  });

  function getScenarioOrVariantById(id: string): ScenarioUpdate | undefined {
    for (const feature of featureUpdateMap.value.values()) {
      for (const scenario of feature.scenarios.values()) {
        if (scenario.uniqueId === id) {
          return scenario;
        }
      }
    }
  }

  const allTestIds = computed(() => {
    const allTestIds = new Array<string>();
    featureUpdateMap.value.forEach((featureUpdate) =>
      allTestIds.push(...featureUpdate.getScenarioIds()),
    );
    return allTestIds;
  });

  function replaceFeatureMap(newFeatureMap: IJsonFeatures) {
    featureUpdateMap.value.clear();
    // a full reset means a new run - a scenario pinned from a previous run no longer applies.
    selectedScenarioId.value = null;
    mergeFeatureMap(newFeatureMap);
  }

  function mergeFeatureMap(newFeatureMap: IJsonFeatures) {
    const targetMap: Map<string, FeatureUpdate> = featureUpdateMap.value;
    FeatureUpdate.addToMapFromJson(targetMap, newFeatureMap);
  }

  function updateFeatureMap(update: Map<string, FeatureUpdate>) {
    update.forEach((featureUpdate: FeatureUpdate, featureKey: string) => {
      if (featureUpdate.description) {
        debug("FEATURE UPDATE " + featureUpdate.description);
        const featureToBeUpdated: FeatureUpdate | undefined =
          featureUpdateMap.value.get(featureKey);
        if (!featureToBeUpdated) {
          // add new feature
          debug(
            "add new feature " +
              featureKey +
              " => " +
              JSON.stringify(featureUpdate),
          );
          const feature = new FeatureUpdate().merge(featureUpdate);
          featureUpdateMap.value.set(featureKey, feature);
          debug(
            "added new feature " + featureKey + " => " + feature.toString(),
          );
        } else {
          featureToBeUpdated.merge(featureUpdate);
        }
      }
    });
  }

  function updateRemovedMessageUuids(update: string[]) {
    featureUpdateMap.value.forEach((featureUpdate: FeatureUpdate) => {
      featureUpdate.scenarios.forEach((scenario) => {
        scenario.steps.forEach((step) => {
          step.rbelMetaData.forEach((metaData) => {
            if (update.includes(metaData.uuid)) {
              debug(
                "Marking message with uuid " + metaData.uuid + " as removed",
              );
              metaData.removed = true;
            }
          });
        });
      });
    });
    debug("Marked messages with uuids as removed:" + update);
  }

  return {
    featureUpdateMap,
    rbelMetadata,
    allTestIds,
    selectedScenarioId,
    scopedScenario,
    scopedScenarioTimeRange,
    scopingActive,
    scopedRbelMetadata,
    toggleScenarioSelection,
    isScenarioSelected,
    toggleScoping,
    replaceFeatureMap,
    getScenarioOrVariantById,
    mergeFeatureMap,
    updateFeatureMap,
    updateRemovedMessageUuids,
  };
});
