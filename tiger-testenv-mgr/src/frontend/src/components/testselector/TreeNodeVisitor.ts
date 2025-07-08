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

import type { TreeNode } from "primevue/treenode";

// Visitor function type
type VisitorFunction = (
  node: TreeNode,
  depth?: number,
  parent?: TreeNode,
) => void;

/**
 * Visits all nodes in a tree structure and applies a visitor function to each node
 * @param nodes - Array of root nodes to start traversal from
 * @param visitor - Function to apply to each node
 */
export function visitTreeNodes(
  nodes: TreeNode[],
  visitor: VisitorFunction,
): void {
  if (!nodes || nodes.length === 0) {
    return;
  }
  visitDepthFirst(nodes, visitor);
}

/**
 * Depth-first traversal (pre-order)
 */
function visitDepthFirst(
  nodes: TreeNode[],
  visitor: VisitorFunction,
  depth: number = 0,
  parent?: TreeNode,
): void {
  for (const node of nodes) {
    // Visit current node
    visitor(node, depth, parent);

    // Recursively visit children
    if (node.children && node.children.length > 0) {
      visitDepthFirst(node.children, visitor, depth + 1, node);
    }
  }
}
