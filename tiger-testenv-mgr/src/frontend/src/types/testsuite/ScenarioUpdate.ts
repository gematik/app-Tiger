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

import TestResult from "./TestResult";
import StepUpdate, { type IJsonSteps } from "./StepUpdate";
import FeatureUpdate from "./FeatureUpdate";
import ScenarioIdentifier from "./ScenarioIdentifier";

interface IScenarioUpdate {
  steps: Map<string, StepUpdate>;
  description: string;
  status: TestResult;
  exampleKeys: Array<string>;
  exampleList: Map<string, string>;
  variantIndex: number;
  uniqueId: string;
  isDryRun: boolean;
}

interface IJsonScenario {
  steps: IJsonSteps;
  description: string;
  status: TestResult;
  exampleKeys: Array<string>;
  exampleList: IJsonOutlineList;
  variantIndex: number;
  uniqueId: string;
  isDryRun: boolean;
}

export interface IJsonScenarios {
  [key: string]: IJsonScenario;
}

export interface IJsonOutlineList {
  [key: string]: string;
}

export default class ScenarioUpdate implements IScenarioUpdate {
  steps = new Map<string, StepUpdate>();
  description = "";
  status = TestResult.UNUSED;
  exampleKeys = new Array<string>();
  exampleList = new Map<string, string>();
  variantIndex = -1;
  uniqueId = "";
  isDryRun = false;

  public static fromJson(json: IJsonScenario): ScenarioUpdate {
    const scenario: ScenarioUpdate = new ScenarioUpdate();
    scenario.steps = StepUpdate.mapFromJson(json.steps);
    scenario.description = json.description;
    if (json.exampleKeys) {
      scenario.exampleKeys = json.exampleKeys;
    }
    if (json.exampleList) {
      scenario.exampleList = this.mapScenarioOutlineFromJson(json.exampleList);
    }
    if (json.variantIndex !== -1) {
      scenario.variantIndex = json.variantIndex;
    }
    if (json.status) {
      scenario.status = json.status;
    } else {
      scenario.status = FeatureUpdate.mapToTestResult(scenario.steps);
    }
    if (json.uniqueId) {
      scenario.uniqueId = json.uniqueId;
    }
    scenario.isDryRun = json.isDryRun;
    return scenario;
  }

  public static mapFromJson(
    jsonscenarios: IJsonScenarios,
  ): Map<string, ScenarioUpdate> {
    const map: Map<string, ScenarioUpdate> = new Map<string, ScenarioUpdate>();
    if (jsonscenarios) {
      Object.entries(jsonscenarios).forEach(([key, value]) =>
        map.set(key, this.fromJson(value)),
      );
    }
    return map;
  }

  public static mapScenarioOutlineFromJson(
    outlineList: IJsonOutlineList,
  ): Map<string, string> {
    const map: Map<string, string> = new Map<string, string>();
    if (outlineList) {
      Object.entries(outlineList).forEach(([key, value]) =>
        map.set(key, value),
      );
    }
    return map;
  }

  public merge(scenario: ScenarioUpdate) {
    if (scenario.description) {
      this.description = scenario.description;
    }
    if (scenario.status) {
      this.status = scenario.status;
    }
    if (scenario.variantIndex !== -1) {
      this.variantIndex = scenario.variantIndex;
    }
    this.isDryRun = scenario.isDryRun;
    if (scenario.steps) {
      for (const key of scenario.steps.keys()) {
        this.mergeStep(key, scenario);
      }
      // update scenario status and change pending steps to skipped in case of error
      this.status = FeatureUpdate.mapToTestResult(this.steps);
      if (this.status === TestResult.FAILED) {
        this.steps.forEach((step) => {
          if (step.status === TestResult.PENDING)
            step.status = TestResult.SKIPPED;
        });
      }
    }
  }

  private mergeStep(key: string, scenario: ScenarioUpdate) {
    const step: StepUpdate | undefined = this.steps.get(key);
    const newStep = scenario.steps.get(key);
    if (newStep) {
      if (step) {
        step.merge(newStep);
      } else {
        this.steps.set(key, newStep);
      }
    } else {
      console.error(
        `RECEIVED a NULL step in scenario ${scenario.description} for key ${key}`,
      );
    }
  }

  public getScenarioIdentifier(): ScenarioIdentifier {
    return new ScenarioIdentifier(this.uniqueId);
  }

  public getLink(featureName: string): string {
    return encodeURIComponent(this.combineScenarioWithFeatureName(featureName));
  }

  public combineScenarioWithFeatureName(featureName: string): string {
    if (this.variantIndex === -1) {
      return featureName.trim() + "_" + this.description.trim();
    } else {
      return (
        featureName.trim() +
        "_" +
        this.description.trim() +
        "[" +
        (this.variantIndex + 1) +
        "]"
      );
    }
  }

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${
      this.status
    }",\nsteps: "${FeatureUpdate.mapToString(this.steps)}"\n}`;
  }
}
