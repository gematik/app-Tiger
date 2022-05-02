/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";

export interface IStep  {
  description: string;
  status: TestResult;
}

export interface IJsonSteps {
  [key:string]: IStep
}

export default class StepUpdate  implements IStep {
  description= "";
  status = TestResult.UNUSED;

  public static fromJson(json: IStep): StepUpdate {
    const step: StepUpdate = new StepUpdate();
    if (json.description) {
      step.description = json.description;
    }
    if (json.status) {
      step.status = json.status;
    }
    return step;
  }

  public static mapFromJson(jsonsteps : IJsonSteps): Map<string, StepUpdate> {
    const map:Map<string, StepUpdate> = new Map<string, StepUpdate>();
    if (jsonsteps) {
      Object.entries(jsonsteps).forEach(([key, value]) =>
          map.set(key, this.fromJson(value))
      );
    }
    return map;
  }

  public merge(step: StepUpdate) {
    if (step.description){
      this.description = step.description;
    }
    if (step.status) {
      this.status = step.status;
    }
  }

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${this.status}"\n }`;
  }
}
