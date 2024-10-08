///
///
/// Copyright 2024 gematik GmbH
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

import FeatureUpdate from "@/types/testsuite/FeatureUpdate";

enum TestResult {
  FAILED = "FAILED",
  PASSED = "PASSED",
  SKIPPED = "SKIPPED",
  PENDING = "PENDING",
  UNDEFINED = "UNDEFINED",
  UNUSED = "UNUSED",
  AMBIGUOUS = "AMBIGUOUS",
  TEST_DISCOVERED = "TEST_DISCOVERED"
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
  if (!iconFamily) {
    iconFamily = "solid"
  }
  if (testResult === 'PASSED') {
    return 'test-passed fa-circle-check fa-' + iconFamily;
  } else if (testResult === 'FAILED') {
    return 'fa-triangle-exclamation fa-solid test-failed';
  } else if (testResult === 'SKIPPED') {
    return 'test-skipped fa-circle-down fa-' + iconFamily;
  } else if (testResult === 'EXECUTING') {
    return 'fa-solid fa-spinner blue fa-spin test-running'; // there is no regular for spinner
  } else if (testResult === 'PENDING') {
    return 'fa-solid fa-spinner fa-spin test-pending'; // there is no regular for spinner
  } else if (testResult === 'TEST_DISCOVERED') {
    return 'test_discovered fa-eye fa-' + iconFamily;
  } else {
    return 'fa-circle-question fa-' + iconFamily;
  }
}


