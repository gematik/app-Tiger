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
import ScenarioUpdate, { type IJsonScenarios } from "./ScenarioUpdate";
import StepUpdate from "@/types/testsuite/StepUpdate";

interface IFeatureUpdate {
  scenarios: Map<string, ScenarioUpdate>;
  description: string;
  status: TestResult;
}

export interface IJsonFeature {
  scenarios: IJsonScenarios;
  description: string;
  status: TestResult;
  sourcePath: string;
}

export interface IJsonFeatures {
  [key: string]: IJsonFeature;
}

export default class FeatureUpdate implements IFeatureUpdate {
  scenarios = new Map<string, ScenarioUpdate>();
  description = "";
  status = TestResult.UNUSED;
  sourcePath = "";

  public computeStatusMessage() {
    if (this.status === TestResult.FAILED) {
      let failed = 0;
      this.scenarios.forEach((scenario) => {
        if (scenario.status === TestResult.FAILED) {
          failed++;
        }
      });
      return `${this.status}: ${failed} failed scenario${failed > 1 ? "s" : ""}`;
    }
    return this.status;
  }

  public static fromJson(json: IJsonFeature): FeatureUpdate {
    const feature = new FeatureUpdate();
    feature.scenarios = ScenarioUpdate.mapFromJson(json.scenarios);
    feature.description = json.description;
    feature.sourcePath = json.sourcePath;
    if (json.status) {
      feature.status = json.status;
    } else {
      feature.status = FeatureUpdate.mapToTestResult(feature.scenarios);
    }

    return feature;
  }

  public static addToMapFromJson(
    map: Map<string, FeatureUpdate>,
    jsonfeatures: IJsonFeatures,
  ) {
    if (jsonfeatures) {
      Object.entries(jsonfeatures).forEach(([key, value]) => {
        if (map.has(key)) {
          map.get(key)?.merge(this.fromJson(value));
        } else {
          map.set(key, this.fromJson(value));
        }
      });
    }
  }

  public merge(feature: FeatureUpdate): FeatureUpdate {
    if (feature.description) {
      this.description = feature.description;
    }
    if (feature.status) {
      this.status = feature.status;
    }
    if (feature.sourcePath) {
      this.sourcePath = feature.sourcePath;
    }

    if (feature.scenarios) {
      for (const key of feature.scenarios.keys()) {
        const scenario: ScenarioUpdate | undefined = this.scenarios.get(key);
        const newScenario = feature.scenarios.get(key);
        if (newScenario) {
          if (scenario) {
            scenario.merge(newScenario);
          } else {
            this.scenarios.set(key, newScenario);
          }
        } else {
          console.error(`No or empty scenario ${key} provided`);
        }
      }
      this.status = FeatureUpdate.mapToTestResult(this.scenarios);
    }
    return this;
  }

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${this.status}",\nsourcePath: "${this.sourcePath}",\nscenario: "${FeatureUpdate.mapToString(this.scenarios)}"\n}`;
  }

  public static mapToString(
    map: Map<string, FeatureUpdate | ScenarioUpdate | StepUpdate>,
  ) {
    let str = `{\n  map: "${typeof map.entries().next()}",`;
    for (const entry of map.entries()) {
      str += `${entry[0]}: ${entry[1].toString()},\n`;
    }
    return str + "\n}";
  }

  public static mapToTestResult(
    map: Map<string, FeatureUpdate | ScenarioUpdate | StepUpdate>,
  ): TestResult {
    let result: TestResult = TestResult.UNUSED;
    map.forEach((entryValue) => {
      if (result !== TestResult.FAILED) {
        switch (entryValue.status) {
          case TestResult.SKIPPED:
          case TestResult.UNDEFINED:
          case TestResult.AMBIGUOUS:
            result = TestResult.FAILED;
            break;
          case TestResult.FAILED:
          case TestResult.PASSED:
          case TestResult.PENDING:
          case TestResult.TEST_DISCOVERED:
            result = entryValue.status;
            break;
        }
      }
    });
    return result;
  }

  public getScenarioIds(): Array<string> {
    const ids = new Array<string>();
    this.scenarios.forEach((scenario) => ids.push(scenario.uniqueId));
    return ids;
  }

  public getLink(featureName: string): string {
    return encodeURIComponent(featureName.trim());
  }
}
