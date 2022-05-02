/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";
import StepUpdate, {IJsonSteps} from "./StepUpdate";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

interface IScenarioUpdate {
  steps: Map<string, StepUpdate>;
  description: string;
  status: TestResult;
}

interface IJsonScenario {
  steps: IJsonSteps;
  description: string;
  status: TestResult;
}

export interface IJsonScenarios {
  [key: string]:IJsonScenario
}

export default class ScenarioUpdate implements IScenarioUpdate{
  steps = new Map<string, StepUpdate>();
  description = "";
  status = TestResult.UNUSED;

  public static fromJson(json: IJsonScenario): ScenarioUpdate {
    const scenario: ScenarioUpdate = new ScenarioUpdate();
    scenario.steps = StepUpdate.mapFromJson(json.steps);
    scenario.description = json.description;
    if (json.status) {
      scenario.status = json.status;
    } else {
      scenario.status = FeatureUpdate.mapToTestResult(scenario.steps);
    }
    return scenario;
  }

  public static mapFromJson(jsonscenarios: IJsonScenarios): Map<string, ScenarioUpdate> {
    const map: Map<string, ScenarioUpdate> = new Map<string, ScenarioUpdate>();
    if (jsonscenarios) {
      Object.entries(jsonscenarios).forEach(([key, value]) =>
          map.set(key, this.fromJson(value))
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
    if (scenario.steps) {
      for (let key of scenario.steps.keys()) {
        const step : StepUpdate | undefined = this.steps.get(key)
        if (step) {
          // @ts-ignore
          step.merge(scenario.steps.get(key))
        } else {
          // @ts-ignore
          this.steps.set(key, scenario.steps.get(key))
        }
      }
      // update scenario status and change pending steps to skipped in case of error
      this.status = FeatureUpdate.mapToTestResult(this.steps);
      if (this.status === TestResult.FAILED) {
        this.steps.forEach(step => {
          if (step.status === TestResult.PENDING) step.status = TestResult.SKIPPED
        });
      }
    }
  }

  public toString() {
    return `{ description: "${this.description}",\nstatus: "${this.status}",\nsteps: "${FeatureUpdate.mapToString(this.steps)}"\n}`;
  }

}
