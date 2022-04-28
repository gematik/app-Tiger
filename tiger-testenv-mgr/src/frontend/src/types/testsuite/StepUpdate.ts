/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";

export default class StepUpdate {
  description: string = "";
  status: TestResult = TestResult.UNUSED;

  constructor() {
  }

  public static fromJson(json: any): StepUpdate {
    const step: StepUpdate = new StepUpdate();
    step.description = json.description;
    step.status = json.status;
    return step;
  }

  public static mapFromJson(jsonsteps : any): Map<string, StepUpdate> {
    const map:Map<string, StepUpdate> = new Map<string, StepUpdate>();
    if (jsonsteps) {
      Object.entries(jsonsteps).forEach(([key, value]) =>
          map.set(key, this.fromJson(value))
      );
    }
    return map;
  }
  public toString() {
    return "description: '" + this.description + "'\nstatus: " + this.status + "\n";
  }

}
