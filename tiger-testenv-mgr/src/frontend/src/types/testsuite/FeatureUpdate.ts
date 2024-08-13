/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import TestResult from "./TestResult";
import ScenarioUpdate, {IJsonScenarios} from "./ScenarioUpdate";
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
}

interface IJsonFeatures {
  [key: string]: IJsonFeature
}

export default class FeatureUpdate implements IFeatureUpdate {
  scenarios = new Map<string, ScenarioUpdate>();
  description = "";
  status = TestResult.UNUSED;

  public static fromJson(json: IJsonFeature): FeatureUpdate {
    const feature = new FeatureUpdate();
    feature.scenarios = ScenarioUpdate.mapFromJson(json.scenarios);
    feature.description = json.description;
    if (json.status) {
      feature.status = json.status;
    } else {
      feature.status = FeatureUpdate.mapToTestResult(feature.scenarios);
    }
    return feature;
  }

  public static addToMapFromJson(map: Map<string, FeatureUpdate>, jsonfeatures: IJsonFeatures) {
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
    if (feature.scenarios) {
      for (const key of feature.scenarios.keys()) {
        const scenario: ScenarioUpdate | undefined = this.scenarios.get(key)
        const newScenario = feature.scenarios.get(key);
        if (newScenario) {
          if (scenario) {
            scenario.merge(newScenario)
          } else {
            this.scenarios.set(key, newScenario)
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
    return `{ description: "${this.description}",\nstatus: "${this.status}",\nscenario: "${FeatureUpdate.mapToString(this.scenarios)}"\n}`;
  }

  public static mapToString(map: Map<string, FeatureUpdate | ScenarioUpdate | StepUpdate>) {
    let str = `{\n  map: "${typeof map.entries().next()}",`;
    for (const entry of map.entries()) {
      str += `${entry[0]}: ${entry[1].toString()},\n`;
    }
    return str + "\n}";
  }

  public static mapToTestResult(map: Map<string, FeatureUpdate | ScenarioUpdate | StepUpdate>): TestResult {
    let result: TestResult = TestResult.UNUSED;
    map.forEach(entryValue => {
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
            result = entryValue.status;
            break;
        }
      }
    });
    return result;
  }
}
