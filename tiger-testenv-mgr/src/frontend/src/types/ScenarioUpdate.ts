import TestResult from "./TestResult";
import StepUpdate from "./StepUpdate";

export default interface ScenarioUpdate {
  steps: Map<string, StepUpdate>;
  description: string;
  status: TestResult;
}
