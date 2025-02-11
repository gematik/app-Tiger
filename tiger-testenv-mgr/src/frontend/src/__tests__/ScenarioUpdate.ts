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

import ScenarioUpdate from "../types/testsuite/ScenarioUpdate";
import TestResult from "../types/testsuite/TestResult";
import each from "jest-each";

describe("testing ScenarioUpdate class", () => {
  const scenarioExample: string =
    '{"steps":{"3":{"description":"Then User requests classes with parameter xyz=4","status":"PASSED","stepIndex":3,"rbelMetaData":[]}},"description":"Test find last request with parameters","exampleKeys":["key1", "key2", "key3"],"exampleList":{"key1": "value1", "key2":"value2", "key3":"value3"},"variantIndex":4, "status": "PASSED"}';

  test("empty ScenarioUpdate should contain undefined values", () => {
    const scenario = new ScenarioUpdate();
    expect(scenario.description).toBe("");
    expect(scenario.status).toBe(TestResult.UNUSED);
    expect(scenario.steps.size).toEqual(0);
    expect(scenario.exampleKeys.length).toEqual(0);
    expect(scenario.exampleList.size).toEqual(0);
    expect(scenario.variantIndex).toEqual(-1);
  });

  test("empty JSON applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(JSON.parse("{ }"));
    expect(scenario.description).toBeUndefined;
    expect(scenario.status).toBe(TestResult.UNUSED);
    expect(scenario.steps).toBeUndefined;
    expect(scenario.exampleKeys).toBeUndefined;
    expect(scenario.exampleList).toBeUndefined;
    expect(scenario.variantIndex).toBeUndefined;
  });

  test("empty JSON description applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse('{ "status": "PASSED" }'),
    );
    expect(scenario.description).toBeUndefined;
    expect(scenario.status).toBe(TestResult.PASSED);
  });

  test("empty JSON status applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse('{ "description": "desc" }'),
    );
    expect(scenario.description).toBe("desc");
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON variantIndex applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse('{ "variantIndex":3 }'),
    );
    expect(scenario.variantIndex).toBe(3);
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON exampleKeys applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(JSON.parse('{ "exampleKeys":[]}'));
    expect(scenario.exampleKeys.length).toBe(0);
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON full exampleKeys applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse('{ "exampleKeys":["key1", "key2", "key3"]}'),
    );
    expect(scenario.exampleKeys.length).toBe(3);
    expect(scenario.exampleKeys[0]).toBe("key1");
    expect(scenario.exampleKeys[1]).toBe("key2");
    expect(scenario.exampleKeys[2]).toBe("key3");
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON full exampleList applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse(
        '{ "exampleList":{"key1": "value1", "key2":"value2", "key3":"value3"}}',
      ),
    );
    expect(scenario.exampleList.size).toBe(3);
    expect(scenario.exampleList.get("key1")).toBe("value1");
    expect(scenario.exampleList.get("key2")).toBe("value2");
    expect(scenario.exampleList.get("key3")).toBe("value3");
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("empty JSON variantIndex applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(
      JSON.parse('{ "variantIndex":3 }'),
    );
    expect(scenario.variantIndex).toBe(3);
    expect(scenario.status).toBe(TestResult.UNUSED);
  });

  test("JSON applies correctly", () => {
    const scenario = ScenarioUpdate.fromJson(JSON.parse(scenarioExample));
    expect(scenario.description).toBe("Test find last request with parameters");
    expect(scenario.exampleList.get("key1")).toBe("value1");
    expect(scenario.exampleList.get("key2")).toBe("value2");
    expect(scenario.exampleList.get("key3")).toBe("value3");
    expect(scenario.variantIndex).toBe(4);
    expect(scenario.exampleKeys[0]).toBe("key1");
    expect(scenario.exampleKeys[1]).toBe("key2");
    expect(scenario.exampleKeys[2]).toBe("key3");
    expect(scenario.status).toBe(TestResult.PASSED);
    expect(scenario.steps.size).toBe(1);
    expect(scenario.steps.get("3")?.description).toBe(
      "Then User requests classes with parameter xyz=4",
    );
    expect(scenario.steps.get("3")?.status).toBe(TestResult.PASSED);
    expect(scenario.steps.get("3")?.stepIndex).toBe(3);
    expect(scenario.steps.get("3")?.rbelMetaData.length).toBe(0);
  });

  const featureJsonDefault = '{ "description": "desc1", "status": "FAILED"}';

  describe("complete merge including steps works correctly", () => {
    each`
        featureJson1           | featureJson2                                         | description          |  testResult
        ${featureJsonDefault}  | ${'{ "description": "desc2", "steps":{"3":{"description":"Then User requests classes with parameter xyz=4","status":"PASSED","stepIndex":3,"rbelMetaData":[]}} }'}  | ${"desc2"}  | ${TestResult.PASSED}
        ${featureJsonDefault}  | ${'{"description": "desc2", "steps":{"3":{"description":"Then User requests classes with parameter xyz=4","status":"PASSED","stepIndex":3,"rbelMetaData":[]}, "1":{"description":"Then User requests something else","status":"FAILED","stepIndex":3,"rbelMetaData":[]}}}'}   | ${"desc2"}   | ${TestResult.FAILED}
        ${featureJsonDefault}  | ${'{ "status": "PENDING" }'}                         | ${"desc1"}            | ${TestResult.UNUSED}
        ${featureJsonDefault}  | ${'{ "description": "desc2", "status": "PENDING" }'} | ${"desc2"}            | ${TestResult.UNUSED}
        ${featureJsonDefault}  | ${"{ }"}                                             | ${"desc1"}            | ${TestResult.UNUSED}
      `.test(
      "for $featureJson1 and $featureJson2 with result $testResult",
      ({ featureJson1, featureJson2, description, testResult }) => {
        const scenario1 = ScenarioUpdate.fromJson(JSON.parse(featureJson1));
        const scenario2 = ScenarioUpdate.fromJson(JSON.parse(featureJson2));
        scenario1.merge(scenario2);
        expect(scenario1.description).toBe(description);
        expect(scenario1.status).toBe(testResult);
      },
    );
  });
});
