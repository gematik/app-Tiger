import DataType from "./DataType";
import TestResult from "./TestResult";

export default interface Step {
  currentDataVariantIndex: number;
  currentStepIndex: number;
  step: string;
  feature: string;
  scenario: string;
  status: TestResult;
  type: DataType;
}

 
