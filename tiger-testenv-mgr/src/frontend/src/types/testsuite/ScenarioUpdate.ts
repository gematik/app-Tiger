/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";
import StepUpdate from "./StepUpdate";
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

export default class ScenarioUpdate {
  steps: Map<string, StepUpdate> = new Map<string, StepUpdate>();
  description: string = "";
  status: TestResult = TestResult.UNUSED;

  constructor() {
  }

  public static fromJson(json: any): ScenarioUpdate {
    const scenario: ScenarioUpdate = new ScenarioUpdate();
    scenario.steps = StepUpdate.mapFromJson(json.steps);
    scenario.description = json.description;
    scenario.status = json.status;
    return scenario;
  }

  public static mapFromJson(jsonscenarios: any): Map<string, ScenarioUpdate> {
    const map: Map<string, ScenarioUpdate> = new Map<string, ScenarioUpdate>();
    if (jsonscenarios) {
      Object.entries(jsonscenarios).forEach(([key, value]) =>
          map.set(key, this.fromJson(value))
      );
    }
    return map;
  }

  public toString() {
    return "description: '" + this.description + "'\nstatus: " + this.status + "\nsteps: " + FeatureUpdate.mapToString(this.steps) + "\n";
  }

};
