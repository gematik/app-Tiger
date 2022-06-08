/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";
import StepUpdate, {IJsonSteps} from "./StepUpdate";
import FeatureUpdate from "./FeatureUpdate";

interface IScenarioUpdate {
  steps: Map<string, StepUpdate>;
  description: string;
  status: TestResult;
  exampleKeys: Array<string>;
  exampleList: Map<string, string>;
  variantIndex: number;
}

interface IJsonScenario {
  steps: IJsonSteps;
  description: string;
  status: TestResult;
  exampleKeys: Array<string>;
  exampleList: IJsonOutlineList;
  variantIndex: number;
}

export interface IJsonScenarios {
  [key: string]:IJsonScenario
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

  public static fromJson(json: IJsonScenario): ScenarioUpdate {
    const scenario: ScenarioUpdate = new ScenarioUpdate();
    scenario.steps = StepUpdate.mapFromJson(json.steps);
    scenario.description = json.description;
    scenario.exampleKeys = json.exampleKeys;
    scenario.exampleList = this.mapScenarioOutlineFromJson(json.exampleList);
    if (json.variantIndex !== -1) {
      scenario.variantIndex = json.variantIndex;
    }
    if (json.status) {
      scenario.status = json.status;
    } else {
      scenario.status = FeatureUpdate.mapToTestResult(scenario.steps);
    }
    return scenario;
  }

  public static mapFromJson(
    jsonscenarios: IJsonScenarios
  ): Map<string, ScenarioUpdate> {
    const map: Map<string, ScenarioUpdate> = new Map<string, ScenarioUpdate>();
    if (jsonscenarios) {
      Object.entries(jsonscenarios).forEach(([key, value]) =>
        map.set(key, this.fromJson(value))
      );
    }
    return map;
  }

  public static mapScenarioOutlineFromJson(outlineList: IJsonOutlineList): Map<string, string> {
    const map: Map<string, string> = new Map<string, string>();
    if (outlineList) {
      Object.entries(outlineList).forEach(([key, value]) =>
        map.set(key, value));
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
    if (scenario.steps) {
      for (const key of scenario.steps.keys()) {
        const step: StepUpdate | undefined = this.steps.get(key);
        const newStep = scenario.steps.get(key);
        if (newStep) {
          if (step) {
            step.merge(newStep);
          } else {
            this.steps.set(key, newStep);
          }
        } else {
          console.error(`RECEIVED a NULL step in scenario ${scenario.description} for key ${key}`);
        }
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

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${
      this.status
    }",\nsteps: "${FeatureUpdate.mapToString(this.steps)}"\n}`;
  }
}
