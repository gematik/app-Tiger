import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

enum TestResult {
  FAILED = "FAILED",
  PASSED = "PASSED",
  SKIPPED = "SKIPPED",
  PENDING = "PENDING",
  UNDEFINED = "UNDEFINED",
  UNUSED = "UNUSED",
  AMBIGUOUS = "AMBIGUOUS",
}

export default TestResult;

export function currentOverallTestRunStatus(featureUpdateMap: Map<string, FeatureUpdate>): string {
  if (!featureUpdateMap.size) {
    return "pending";
  }
  let status = "passed";
  featureUpdateMap.forEach(feature => {
    if (feature.status === TestResult.FAILED) {
      status = "failed";
    }
    feature.scenarios.forEach(scenario => {
      if (scenario.status === TestResult.FAILED) {
        status = "failed";
      }
    });
  });
  return status;
}

export function getTestResultIcon(testResult: string, iconFamily: string): string {
  if (!iconFamily) { iconFamily = "solid"}
  if (testResult === 'PASSED') {
    return 'fa-circle-check fa-' + iconFamily;
  } else if (testResult === 'FAILED') {
    return 'fa-triangle-exclamation fa-solid';
  } else if (testResult === 'SKIPPED') {
    return 'fa-circle-down fa-' + iconFamily;
  } else if (testResult === 'PENDING') {
    return 'fa-solid fa-spinner'; // there is no regular for spinner
  } else {
    return 'fa-circle-question fa-' + iconFamily;
  }
}


