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

import { convertToTreeNode } from "../components/testselector/FeatureToTreeNodeConverter";
import FeatureUpdate from "../types/testsuite/FeatureUpdate";
import ScenarioUpdate from "../types/testsuite/ScenarioUpdate";
import TestResult from "@/types/testsuite/TestResult.ts";

describe("FeatureToTreeNodeConverter", () => {
  function createFeatureUpdate(
    description: string,
    sourcePath: string,
    scenarios?: Map<string, ScenarioUpdate>,
  ): FeatureUpdate {
    const featureUpdate = new FeatureUpdate();
    featureUpdate.sourcePath = sourcePath;
    featureUpdate.description = description;
    featureUpdate.scenarios = scenarios ?? new Map<string, ScenarioUpdate>();
    return featureUpdate;
  }

  function createScenarioUpdate(id: string, name: string): ScenarioUpdate {
    const scenarioUpdate = new ScenarioUpdate();
    scenarioUpdate.uniqueId = id;
    scenarioUpdate.description = name;
    return scenarioUpdate;
  }

  it("should return an empty array for an empty feature map", () => {
    const result = convertToTreeNode(new Map());
    expect(result).toEqual([]);
  });

  it("should create a single feature node without scenarios", () => {
    const featureMap = new Map<string, FeatureUpdate>();
    featureMap.set(
      "feature1",
      createFeatureUpdate("feature1", "/features/feature1.feature"),
    );
    const result = convertToTreeNode(featureMap);
    expect(result.length).toBe(1);
    expect(result[0].type).toBe("feature");
    expect(result[0].data.label).toBe("feature1");
  });

  it("should create folder nodes for features in subfolders", () => {
    const featureMap = new Map<string, FeatureUpdate>();
    featureMap.set(
      "feature1",
      createFeatureUpdate("feature1", "/features/a/feature1.feature"),
    );
    featureMap.set(
      "feature2",
      createFeatureUpdate("feature2", "/features/b/feature2.feature"),
    );
    const result = convertToTreeNode(featureMap);
    // Should have a folder node for 'a' and 'b', each with a feature child
    const folderA = result.find((n) => n.label === "a");
    const folderB = result.find((n) => n.label === "b");
    expect(folderA).toBeDefined();
    expect(folderB).toBeDefined();
    expect(folderA!.children![0].type).toBe("feature");
    expect(folderB!.children![0].type).toBe("feature");
  });

  it("should add scenario children to feature nodes", () => {
    const scenarios = new Map<string, ScenarioUpdate>();
    scenarios.set(
      "[feature:1]/[scenario:1]",
      createScenarioUpdate("[scenario:1]", "Scenario 1"),
    );
    scenarios.set(
      "[feature:1]/[scenario:2]",
      createScenarioUpdate("[scenario:2]", "Scenario 2"),
    );
    const featureMap = new Map<string, FeatureUpdate>();
    featureMap.set(
      "feature1",
      createFeatureUpdate("feature1", "/features/feature1.feature", scenarios),
    );
    const result = convertToTreeNode(featureMap);
    const featureNode = result[0];
    expect(featureNode.children).toBeDefined();
    expect(featureNode.children).toHaveLength(2);
    expect(featureNode.children![0].data.label).toBe("Scenario 1");
    expect(featureNode.children![1].data.label).toBe("Scenario 2");
  });

  it("should create only one node for the scenario when unique id includes driver class segment", () => {
    const featureMap = new Map<string, FeatureUpdate>();
    featureMap.set(
      "this is a feature in the catalog folder",
      FeatureUpdate.fromJson({
        scenarios: {
          "[engine:junit-platform-suite]/[suite:de.gematik.test.tiger.examples.bdd.drivers.Driver004IT]/[engine:cucumber]/[feature:file%3A%2FC%3A%2Frepos%2Ftiger-demo%2Fsrc%2Ftest%2Fresources%2Ffeatures%2FCatalog%2Fcatalog.feature]/[scenario:3]":
            {
              steps: {
                "0": {
                  description: "Given TGR zeige Banner &quot;Hello&quot;",
                  status: TestResult.PENDING,
                  failureMessage: "",
                  failureStacktrace: "",
                  tooltip: 'Given TGR zeige Banner "Hello"',
                  stepIndex: 0,
                  rbelMetaData: [],
                  subSteps: [],
                },
              },
              description:
                "This is also a scenario from the feature in the catalog folder",
              status: TestResult.TEST_DISCOVERED,
              failureMessage: "",
              exampleKeys: [],
              exampleList: {},
              variantIndex: -1,
              uniqueId:
                "[engine:junit-platform-suite]/[suite:de.gematik.test.tiger.examples.bdd.drivers.Driver004IT]/[engine:cucumber]/[feature:file%3A%2FC%3A%2Frepos%2Ftiger-demo%2Fsrc%2Ftest%2Fresources%2Ffeatures%2FCatalog%2Fcatalog.feature]/[scenario:3]",
              isDryRun: true,
            },
        },
        description: "this is a feature in the catalog folder",
        status: TestResult.UNDEFINED,
        sourcePath:
          "file:///C:/repos/tiger-demo/src/test/resources/features/Catalog/catalog.feature",
      }),
    );

    const result = convertToTreeNode(featureMap);
    const featureNode = result[0];
    expect(featureNode.children).toBeDefined();
    expect(featureNode.children).toHaveLength(1);
    expect(featureNode.children![0].data.label).toBe(
      "This is also a scenario from the feature in the catalog folder",
    );
    expect(featureNode.children![0].children).toHaveLength(0);
  });
});
