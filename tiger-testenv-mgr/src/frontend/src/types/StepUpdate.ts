import TestResult from "./TestResult";

export default interface StepUpdate {
  description: string;
  status: TestResult;
}
