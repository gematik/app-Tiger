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

import FeatureUpdate from "@/types/testsuite/FeatureUpdate.ts";
import type ScenarioUpdate from "@/types/testsuite/ScenarioUpdate.ts";
import type { TreeNode } from "primevue/treenode";
import { visitTreeNodes } from "@/components/testselector/TreeNodeVisitor.ts";
import commonPathPrefix from "common-path-prefix";

function createSingleFeatureNode(featureUpdateMap: Map<string, FeatureUpdate>) {
  //when we only have one feature, we dont need to show the folder structure
  const singleFeatureKey = featureUpdateMap.keys().next().value;
  const singleFeatureValue = featureUpdateMap.values().next().value;

  return [
    {
      key: singleFeatureKey,
      data: {
        label: singleFeatureValue?.description,
        sourcePath: singleFeatureValue?.sourcePath.substring(
          singleFeatureValue.sourcePath.lastIndexOf("/") + 1,
        ),
        selected: false,
      },
      children: [],
      type: "feature",
    } as TreeNode,
  ];
}

/**
 * adds to the nodes list a node for each feature in the featureUpdatemap with label and path,
 * but not yet with children nodes.
 */
function buildFolderAndFeatureNodes(
  featureUpdateMap: Map<string, FeatureUpdate>,
) {
  if (featureUpdateMap.size === 1) {
    return createSingleFeatureNode(featureUpdateMap);
  }
  const commonPath = findFeaturesCommonPath(
    Array.from(featureUpdateMap.values()),
  );

  // Helper to ensure unique folder nodes
  const folderNodeMap = new Map<string, TreeNode>();
  const rootNode: TreeNode = {
    key: "root",
    type: "root",
    children: [],
  };

  for (const [featureKey, featureUpdate] of featureUpdateMap) {
    // Get the relative path from the common path
    const pathRelativeToCommonPath = featureUpdate.sourcePath.replace(
      commonPath,
      "",
    );
    const parts = pathRelativeToCommonPath.split(/[\\/]/); // split on both / and \
    //remove trailing / or \ from common
    let currentPath = "";
    let parentNode = rootNode;

    // Build folder nodes for each part except the last (which is the file)
    for (let i = 0; i < parts.length - 1; i++) {
      currentPath += "/" + parts[i];
      if (!folderNodeMap.has(currentPath)) {
        const folderNode: TreeNode = {
          key: currentPath,
          label: parts[i],
          data: {
            label: parts[i],
            path: currentPath,
          },
          children: [],
          type: "folder",
        };
        folderNodeMap.set(currentPath, folderNode);
        parentNode.children!.push(folderNode);
      }
      parentNode = folderNodeMap.get(currentPath)!;
    }

    // Create the feature node
    const featureNode: TreeNode = {
      key: featureKey,
      data: {
        label: featureUpdate.description,
        sourcePath: pathRelativeToCommonPath,
        selected: false,
      },
      type: "feature",
    };

    // Attach feature node to its parent folder, or root if no folders
    parentNode.children!.push(featureNode);
  }
  return rootNode.children!;
}

function findFeaturesCommonPath(features: FeatureUpdate[]): string {
  const paths = features.map((feature) => feature.sourcePath);
  return commonPathPrefix(paths);
}

function convertScenarioListToTree(
  scenarios: Map<string, ScenarioUpdate> | undefined,
  shortenedDisplayPath: string,
) {
  return scenarios ? buildTestTree(scenarios, shortenedDisplayPath) : [];
}

export function convertToTreeNode(
  featureUpdateMap: Map<string, FeatureUpdate>,
): TreeNode[] {
  const nodes: TreeNode[] = buildFolderAndFeatureNodes(featureUpdateMap);
  visitTreeNodes(nodes, (node) =>
    addScenariosAsNodeChildren(node, featureUpdateMap),
  );
  return nodes;
}

function addScenariosAsNodeChildren(
  node: TreeNode,
  featureUpdateMap: Map<string, FeatureUpdate>,
) {
  if (node.type === "feature") {
    const feature = featureUpdateMap.get(node.key);
    if (feature) {
      const scenarios = feature.scenarios;
      node.children = convertScenarioListToTree(
        scenarios,
        node.data.sourcePath,
      );
    }
  }
}

interface ParsedSegment {
  type: string;
  value: string;
}

/**
 * Parses a segment like "[scenario:73]" into type and value
 */
function parseSegment(segment: string): ParsedSegment {
  const match = RegExp(/^\[([^:]+):(.+)]$/).exec(segment);
  if (!match) {
    throw new Error(`Invalid segment format: ${segment}`);
  }
  return {
    type: match[1],
    value: match[2],
  };
}

function parseTestId(id: string): ParsedSegment[] {
  // Split by '/'
  const segments = id.split("/");
  return segments.map((segment) => {
    return parseSegment(segment);
  });
}

function determineTestType(
  segment: ParsedSegment,
  segments: ParsedSegment[],
): string {
  const lastSegment = segments[segments.length - 1];
  if (segment === lastSegment) {
    if (segment.type === "example") {
      return "scenarioVariant";
    } else {
      return segment.type;
    }
  } else if (segment.type == "scenario") {
    return "scenarioOutline";
  } else {
    return segment.type;
  }
}

const visibleSegmentTypesInUi = ["scenario", "example"];

function buildTestTree(
  scenarios: Map<string, ScenarioUpdate>,
  shortenedDisplayPath: string,
): TreeNode[] {
  const testIds = Array.from(scenarios.keys());
  const root: TreeNode[] = [];

  for (const testId of testIds) {
    const segments = parseTestId(testId);
    let currentLevel: TreeNode[] = root;
    let currentPath = "";

    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      currentPath += (i > 0 ? "/" : "") + `[${segment.type}:${segment.value}]`;

      // Look for existing node at this level
      let existingNode: TreeNode | undefined = currentLevel.find(
        (node) =>
          node.type === segment.type &&
          node.data.segmentValue === segment.value,
      );

      if (visibleSegmentTypesInUi.includes(segment.type)) {
        if (!existingNode) {
          const scenario = scenarios.get(testId)!;
          existingNode = createNodeForScenario(
            currentPath,
            scenario,
            segment,
            determineTestType(segment, segments),
            shortenedDisplayPath,
          );
          currentLevel?.push(existingNode);
        }
        // Move to next level
        currentLevel = existingNode.children ?? [];
      }
    }
  }
  return root;
}

function createNodeForScenario(
  key: string,
  scenario: ScenarioUpdate,
  segment: ParsedSegment,
  testType: string,
  sourcePath: string,
): TreeNode {
  return {
    key: key,
    data: {
      label: scenario?.description,
      examples: scenario?.getVariantExamplesAsString(),
      segmentValue: segment.value,
      testType: testType,
      sourcePath: sourcePath,
    },
    type: segment.type,
    children: [],
  };
}
