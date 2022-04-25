import TestResult from "./TestResult";
import ScenarioUpdate from "./ScenarioUpdate";

export default interface FeatureUpdate {
  scenarios: Map<string, ScenarioUpdate>;
  description: string;
  status: TestResult;
}
