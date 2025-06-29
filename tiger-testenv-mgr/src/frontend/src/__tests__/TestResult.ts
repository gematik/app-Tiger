///
///
/// Copyright 2021-2025 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import FeatureUpdate from "../types/testsuite/FeatureUpdate";
import { currentOverallTestRunStatus } from "../types/testsuite/TestResult";
import each from "jest-each";

describe("testing TestResult class", () => {
  const featureExampleWithPassedSteps: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE","scenarios":{"b99":{"steps":{"3":{"status":"PASSED"}}}}}';
  const featureExampleWithFailedSteps: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"FAILED"}}}}}';
  const featureExampleWithPendingSteps: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"PENDING"}}}}}';
  const featureExampleWithSkippedSteps: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"SKIPPED"}}}}}';
  const featureExampleWithUnsuedSteps: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"UNUSED"}}}}}';

  describe("currentOverallTestRunStatus should pass", () => {
    each`
        featureJson1                      | featureJson2                        | testResult
        ${featureExampleWithPassedSteps}  | ${featureExampleWithPassedSteps}    | ${"passed"}
        ${featureExampleWithPassedSteps}  | ${featureExampleWithFailedSteps}    | ${"failed"}
        ${featureExampleWithPassedSteps}  | ${featureExampleWithPendingSteps}   | ${"passed"}
        ${featureExampleWithPendingSteps} | ${featureExampleWithPendingSteps}   | ${"passed"}        
        ${featureExampleWithFailedSteps}  | ${featureExampleWithFailedSteps}    | ${"failed"}                
        ${featureExampleWithSkippedSteps} | ${featureExampleWithFailedSteps}    | ${"failed"}  
        ${featureExampleWithSkippedSteps} | ${featureExampleWithPassedSteps}    | ${"failed"}  
        ${featureExampleWithSkippedSteps} | ${featureExampleWithSkippedSteps}   | ${"failed"}     
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithFailedSteps}    | ${"failed"}  
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithPassedSteps}    | ${"passed"}  
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithUnsuedSteps}    | ${"passed"}       
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithSkippedSteps}   | ${"failed"}  
      `.test(
      "for $featureJson1 and $featureJson2 with result $testResult",
      ({ featureJson1, featureJson2, testResult }) => {
        const feature1 = FeatureUpdate.fromJson(JSON.parse(featureJson1));
        const feature2 = FeatureUpdate.fromJson(JSON.parse(featureJson2));
        const map: Map<string, FeatureUpdate> = new Map<
          string,
          FeatureUpdate
        >();
        map.set("1", feature1);
        map.set("2", feature2);
        const result = currentOverallTestRunStatus(map);
        expect(result).toBe(testResult);
      },
    );
  });

  test("currentOverallTestRunStatus should be pending when map is empty", () => {
    const map: Map<string, FeatureUpdate> = new Map<string, FeatureUpdate>();
    const result = currentOverallTestRunStatus(map);
    expect(result).toBe("pending");
  });
});
