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

import { visitTreeNodes } from "../components/testselector/TreeNodeVisitor";
import { TreeNode } from "primevue/treenode";

describe("visitTreeNodes", () => {
  const tree: TreeNode[] = [
    {
      key: "root",
      label: "Root Node",
      data: { type: "folder" },
      children: [
        {
          key: "child1",
          label: "Child 1",
          data: { type: "file" },
        },
        {
          key: "child2",
          label: "Child 2",
          data: { type: "folder" },
          children: [
            {
              key: "grandchild1",
              label: "Grandchild 1",
              data: { type: "file" },
            },
            {
              key: "grandchild2",
              label: "Grandchild 2",
              data: { type: "file" },
            },
          ],
        },
      ],
    },
  ];

  it("should visit all nodes in depth-first order", () => {
    const visited: string[] = [];
    visitTreeNodes(tree, (node) => visited.push(node.key));
    expect(visited).toEqual([
      "root",
      "child1",
      "child2",
      "grandchild1",
      "grandchild2",
    ]);
  });

  it("should return an empty array for an empty tree", () => {
    const visited: string[] = [];
    visitTreeNodes([], (node) => visited.push(node.key));
    expect(visited).toEqual([]);
  });
});
