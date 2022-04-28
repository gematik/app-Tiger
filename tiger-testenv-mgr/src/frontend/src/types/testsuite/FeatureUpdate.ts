/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TestResult from "./TestResult";
import ScenarioUpdate from "./ScenarioUpdate";
import StepUpdate from "@/types/testsuite/StepUpdate";

export default class FeatureUpdate {
  scenarios: Map<string, ScenarioUpdate> = new Map<string, ScenarioUpdate>();
  description: string = "";
  status: TestResult = TestResult.UNUSED;

  constructor() {

  }

  public static fromJson(json:any) : FeatureUpdate {
    const feature = new FeatureUpdate();
    feature.scenarios = ScenarioUpdate.mapFromJson(json.scenarios);
    feature.description = json.description;
    feature.status = json.status;
    return feature;
  }

  public static addToMapFromJson(map: Map<string, FeatureUpdate>, jsonfeatures: any) {
    if (jsonfeatures) {
      Object.entries(jsonfeatures).forEach(([key, value]) => {
        if (map.has(key)) {
          // @ts-ignore
          map.get(key).merge(this.fromJson(value));
        } else {
          map.set(key, this.fromJson(value));
        }
      });
    }
  }

  public merge(feature: FeatureUpdate) {
    if (feature.description) {
      this.description = feature.description;
    }
    if (feature.status) {
      this.status = feature.status;
    }
    if (feature.scenarios) {
      for (let key of feature.scenarios.keys()) {
        // @ts-ignore
        this.scenarios.set(key, feature.scenarios.get(key))
      }
    }
  }

  public toString() {
    return "description: '" + this.description + "'\nstatus: " + this.status + "\nscenario: " + FeatureUpdate.mapToString(this.scenarios) + "\n";
  }

  public static mapToString(map: Map<string, FeatureUpdate | ScenarioUpdate | StepUpdate>) {
    let str = "[\n";
    for (const entry of map.entries()) {
      str += "  KEY " + entry[0] + ": { " + entry[1].toString() + "}\n";
    }
    return str + "]";
  }
}
