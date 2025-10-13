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
import MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto";
import { type IMismatchNote } from "./MismatchNote";

export interface IStep {
  description: string;
  tooltip: string;
  status: TestResult;
  failureMessage: string;
  failureStacktrace: string;
  rbelMetaData: MessageMetaDataDto[];
  stepIndex: number;
  subSteps: IStep[];
  mismatchNotes: IMismatchNote[];
}

export interface IJsonSteps {
  [key: string]: IStep;
}

export default class StepUpdate implements IStep {
  description = "";
  tooltip = "";
  status = TestResult.UNUSED;
  failureMessage = "";
  failureStacktrace = "";
  stepIndex = -1;
  rbelMetaData: MessageMetaDataDto[] = [];
  subSteps: IStep[] = [];
  mismatchNotes: IMismatchNote[] = [];

  public static fromJson(json: IStep): StepUpdate {
    const step: StepUpdate = new StepUpdate();
    if (json.description) {
      step.description = json.description;
    }
    if (json.tooltip) {
      step.tooltip = json.tooltip;
    }
    if (json.status) {
      step.status = json.status;
    }
    if (json.failureMessage) {
      step.failureMessage = json.failureMessage;
    }
    if (json.failureStacktrace) {
      step.failureStacktrace = json.failureStacktrace;
    }
    if (json.rbelMetaData?.length) {
      step.rbelMetaData = json.rbelMetaData;
    }
    if (json.stepIndex !== -1) {
      step.stepIndex = json.stepIndex;
    }
    if (json.subSteps?.length) {
      step.subSteps = json.subSteps;
    }
    if (json.mismatchNotes?.length) {
      step.mismatchNotes = json.mismatchNotes;
    }
    return step;
  }

  public static mapFromJson(
    jsonsteps: IJsonSteps | undefined | null,
  ): Map<string, StepUpdate> {
    const map: Map<string, StepUpdate> = new Map<string, StepUpdate>();
    if (jsonsteps) {
      Object.entries(jsonsteps).forEach(([key, value]) =>
        map.set(key, this.fromJson(value)),
      );
    }
    return map;
  }

  public merge(step: StepUpdate) {
    if (step.description) {
      this.description = step.description;
    }
    if (step.tooltip) {
      this.tooltip = step.tooltip;
    }
    if (step.status) {
      this.status = step.status;
    }
    if (step.status && step.status !== TestResult.FAILED) {
      this.failureMessage = "";
      this.failureStacktrace = "";
    } else {
      if (step.failureMessage) {
        this.failureMessage = step.failureMessage;
      }
      if (step.failureStacktrace) {
        this.failureStacktrace = step.failureStacktrace;
      }
    }
    if (step.rbelMetaData?.length) {
      this.rbelMetaData = step.rbelMetaData;
    }
    if (step.stepIndex !== -1) {
      this.stepIndex = step.stepIndex;
    }
    if (step.subSteps?.length) {
      this.subSteps = step.subSteps;
    }
    if (step.mismatchNotes?.length) {
      this.mismatchNotes = step.mismatchNotes;
    }
  }

  public toString() {
    return JSON.stringify(this);
  }
}
