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

import FeatureUpdate from "../types/testsuite/FeatureUpdate";
import TestResult from "../types/testsuite/TestResult";
import ScenarioUpdate from "../types/testsuite/ScenarioUpdate";
import each from "jest-each";

describe("testing FeatureUpdate class", () => {
  const featureExample: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"description":"Then User requests classes with parameter xyz=4","status":"PASSED","stepIndex":3,"rbelMetaData":[]}},"description":"Test find last request with parameters","exampleKeys":["key1", "key2", "key3"],"exampleList":{"key1": "value1", "key2":"value2", "key3":"value3"},"variantIndex":4}}}';
  const featureExampleWithFailedStep: string =
    '{"description":"Test Tiger BDD","servers":{},"index":98,"bannerType":"MESSAGE", "scenarios":{"b99":{"steps":{"3":{"description":"Then User requests classes with parameter xyz=4","status":"FAILED","stepIndex":3,"rbelMetaData":[]}},"description":"Test find last request with parameters","exampleKeys":["key1", "key2", "key3"],"exampleList":{"key1": "value1", "key2":"value2", "key3":"value3"},"variantIndex":4}}}';

  test("empty FeatureUpdate should contain undefined values", () => {
    const feature = new FeatureUpdate();
    expect(feature.description).toBe("");
    expect(feature.status).toBe(TestResult.UNUSED);
    expect(feature.scenarios.size).toEqual(0);
  });

  test("empty JSON applies correctly", () => {
    const feature = FeatureUpdate.fromJson(JSON.parse("{ }"));
    expect(feature.description).toBeUndefined;
    expect(feature.status).toBe(TestResult.UNUSED);
    expect(feature.scenarios).toBeUndefined;
  });

  test("empty JSON description applies correctly", () => {
    const feature = FeatureUpdate.fromJson(
      JSON.parse('{ "status": "PASSED" }'),
    );
    expect(feature.description).toBeUndefined;
    expect(feature.status).toBe(TestResult.PASSED);
  });

  test("empty JSON status applies correctly", () => {
    const feature = FeatureUpdate.fromJson(
      JSON.parse('{ "description": "desc" }'),
    );
    expect(feature.description).toBe("desc");
    expect(feature.status).toBe(TestResult.UNUSED);
  });

  test("JSON applies correctly", () => {
    const feature = FeatureUpdate.fromJson(JSON.parse(featureExample));
    expect(feature.description).toBe("Test Tiger BDD");
    expect(feature.status).toBe(TestResult.PASSED);
    expect(feature.scenarios.size).toBe(1);
    expect(feature.scenarios.get("b99")?.steps.size).toBe(1);
  });

  describe("complete merge including scenario and steps works correctly", () => {
    each`
        featureJson1                                         | featureJson2                                         | description          | size | testResult
        ${'{ "description": "desc1", "status": "FAILED" }'}  | ${featureExample}                                    | ${"Test Tiger BDD"}  | ${1} | ${TestResult.PASSED}
        ${'{ "description": "desc1", "status": "FAILED" }'}  | ${featureExampleWithFailedStep}                      | ${"Test Tiger BDD"}  | ${1} | ${TestResult.FAILED}
        ${'{ "description": "desc1", "status": "FAILED" }'}  | ${'{ "status": "PENDING" }'}                         | ${"desc1"}           | ${0} | ${TestResult.UNUSED}
        ${'{ "description": "desc1", "status": "FAILED" }'}  | ${'{ "description": "desc2", "status": "PENDING" }'} | ${"desc2"}           | ${0} | ${TestResult.UNUSED}
        ${'{ "description": "desc1", "status": "FAILED" }'}  | ${"{ }"}                                             | ${"desc1"}           | ${0} | ${TestResult.UNUSED}
      `.test(
      "for $featureJson1 and $featureJson2 with result $testResult",
      ({ featureJson1, featureJson2, description, size, testResult }) => {
        const feature1 = FeatureUpdate.fromJson(JSON.parse(featureJson1));
        const feature2 = FeatureUpdate.fromJson(JSON.parse(featureJson2));
        feature1.merge(feature2);
        expect(feature1.description).toBe(description);
        expect(feature1.status).toBe(testResult);
        expect(feature1.scenarios.size).toBe(size);
      },
    );
  });

  describe("mapToTestResult for scenarios works correctly", () => {
    each`
        testResult   | json
        ${TestResult.FAILED}  | ${'{"description":"Bla","exampleKeys":[],"exampleList":{},"variantIndex":4,"steps":{"3":{"description":"Then User requests bla","status":"PASSED","stepIndex":3,"rbelMetaData":[]}, "4":{"description":"Then User requests blub","status":"SKIPPED","stepIndex":4,"rbelMetaData":[]}}}'}
        ${TestResult.PENDING} | ${'{"description":"Bla","exampleKeys":[],"exampleList":{},"variantIndex":4,"steps":{"3":{"description":"Then User requests bla","status":"PASSED","stepIndex":3,"rbelMetaData":[]}, "4":{"description":"Then User requests blub","status":"PENDING","stepIndex":4,"rbelMetaData":[]}}}'}
        ${TestResult.PASSED}  | ${'{"description":"Bla","exampleKeys":[],"exampleList":{},"variantIndex":4,"steps":{"3":{"description":"Then User requests bla","status":"PASSED","stepIndex":3,"rbelMetaData":[]}, "4":{"description":"Then User requests blub","status":"PASSED","stepIndex":4,"rbelMetaData":[]}}}'}
        ${TestResult.PASSED}  | ${'{"description":"Then User requests classes with parameter xyz=4","status":"PASSED"}'}
       `.test("for $json with result $testResult", ({ json, testResult }) => {
      const scenarioUpdate = ScenarioUpdate.fromJson(JSON.parse(json));
      const map: Map<string, ScenarioUpdate> = new Map<
        string,
        ScenarioUpdate
      >();
      map.set("1", scenarioUpdate);
      const result = FeatureUpdate.mapToTestResult(map);
      expect(result).toBe(testResult);
    });
  });

  describe("mapToTestResult for feature works correctly", () => {
    each`
        testResult   | json
        ${TestResult.FAILED}  | ${featureExampleWithFailedStep}
        ${TestResult.PASSED}  | ${featureExample}
      `.test("for $json with result $testResult", ({ json, testResult }) => {
      const featureUpdate = FeatureUpdate.fromJson(JSON.parse(json));
      const map: Map<string, FeatureUpdate> = new Map<string, FeatureUpdate>();
      map.set("1", featureUpdate);
      const result = FeatureUpdate.mapToTestResult(map);
      expect(result).toBe(testResult);
    });
  });
});
