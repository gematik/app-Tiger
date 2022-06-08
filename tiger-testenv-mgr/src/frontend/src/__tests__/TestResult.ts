/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import FeatureUpdate from "../types/testsuite/FeatureUpdate";
import {currentOverallTestRunStatus} from "../types/testsuite/TestResult";
import each from "jest-each";

describe("testing TestResult class", () => {
  const featureExampleWithPassedSteps:string = '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE","scenarios":{"b99":{"steps":{"3":{"status":"PASSED"}}}}}';
  const featureExampleWithFailedSteps:string = '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"FAILED"}}}}}';
  const featureExampleWithPendingSteps:string = '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"PENDING"}}}}}';
  const featureExampleWithSkippedSteps:string = '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"SKIPPED"}}}}}';
  const featureExampleWithUnsuedSteps:string = '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"status":"UNUSED"}}}}}';

  describe('currentOverallTestRunStatus should pass', () => {
    each`
        featureJson1                      | featureJson2                        | testResult
        ${featureExampleWithPassedSteps}  | ${featureExampleWithPassedSteps}    | ${'passed'}
        ${featureExampleWithPassedSteps}  | ${featureExampleWithFailedSteps}    | ${'failed'}
        ${featureExampleWithPassedSteps}  | ${featureExampleWithPendingSteps}   | ${'passed'}
        ${featureExampleWithPendingSteps} | ${featureExampleWithPendingSteps}   | ${'passed'}        
        ${featureExampleWithFailedSteps}  | ${featureExampleWithFailedSteps}    | ${'failed'}                
        ${featureExampleWithSkippedSteps} | ${featureExampleWithFailedSteps}    | ${'failed'}  
        ${featureExampleWithSkippedSteps} | ${featureExampleWithPassedSteps}    | ${'failed'}  
        ${featureExampleWithSkippedSteps} | ${featureExampleWithSkippedSteps}   | ${'failed'}     
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithFailedSteps}    | ${'failed'}  
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithPassedSteps}    | ${'passed'}  
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithUnsuedSteps}    | ${'passed'}       
        ${featureExampleWithUnsuedSteps}  | ${featureExampleWithSkippedSteps}   | ${'failed'}  
      `.test('for $featureJson1 and $featureJson2 with result $testResult', ({featureJson1, featureJson2, testResult}) => {
      let feature1 = FeatureUpdate.fromJson(JSON.parse(featureJson1));
      let feature2 = FeatureUpdate.fromJson(JSON.parse(featureJson2));
      let map: Map<string, FeatureUpdate> = new Map<string, FeatureUpdate>();
      map.set("1", feature1);
      map.set("2", feature2);
      let result = currentOverallTestRunStatus(map);
      expect(result).toBe(testResult);
    });
  });

  test("currentOverallTestRunStatus should be pending when map is empty", () => {
    let map: Map<string, FeatureUpdate> = new Map<string, FeatureUpdate>();
    let result = currentOverallTestRunStatus(map);
    expect(result).toBe("pending");
  });
});
