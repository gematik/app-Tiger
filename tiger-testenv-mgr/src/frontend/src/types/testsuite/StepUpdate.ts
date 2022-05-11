/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import TestResult from "./TestResult";

export interface IStep  {
  description: string;
  status: TestResult;
  stepIndex: number;
}

export interface IJsonSteps {
  [key:string]: IStep
}

export default class StepUpdate implements IStep {
  description = "";
  status = TestResult.UNUSED;
  stepIndex = -1;

  public static fromJson(json: IStep): StepUpdate {
    const step: StepUpdate = new StepUpdate();
    if (json.description) {
      step.description = json.description;
    }
    if (json.status) {
      step.status = json.status;
    }
    if (json.stepIndex !== -1) {
      step.stepIndex = json.stepIndex;
    }
    return step;
  }

  public static mapFromJson(jsonsteps: IJsonSteps): Map<string, StepUpdate> {
    const map: Map<string, StepUpdate> = new Map<string, StepUpdate>();
    if (jsonsteps) {
      Object.entries(jsonsteps).forEach(([key, value]) =>
        map.set(key, this.fromJson(value))
      );
    }
    return map;
  }

  public merge(step: StepUpdate) {
    if (step.description) {
      this.description = step.description;
    }
    if (step.status) {
      this.status = step.status;
    }
    if (step.stepIndex) {
      this.stepIndex = step.stepIndex;
    }      
  }

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${this.status}",\nstepIndex: "${this.stepIndex}"\n }`;
  }
}
