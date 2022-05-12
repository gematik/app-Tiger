/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";
import MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto";

export interface IStep  {
  description: string;
  status: TestResult;
  rbelMetaData: MessageMetaDataDto[];
  stepIndex: number;
}

export interface IJsonSteps {
  [key:string]: IStep
}

export default class StepUpdate  implements IStep {
  description= "";
  status = TestResult.UNUSED;
  stepIndex = -1;
  rbelMetaData = new Array();

  public static fromJson(json: IStep): StepUpdate {
    const step: StepUpdate = new StepUpdate();
    if (json.description) {
      step.description = json.description;
    }
    if (json.status) {
      step.status = json.status;
    }
    if (json.rbelMetaData?.length) {
      step.rbelMetaData = json.rbelMetaData;
    }
    if (json.stepIndex !== -1) {
      step.stepIndex = json.stepIndex;
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
    if (step.description) {
      this.description = step.description;
    }
    if (step.status) {
      this.status = step.status;
    }
    if (step.rbelMetaData?.length) {
      this.rbelMetaData = step.rbelMetaData;
    }
    if (step.stepIndex) {
      this.stepIndex = step.stepIndex;
    }
  }

  public toString() {
    return JSON.stringify(this, () => {} ,2);
  }
}
