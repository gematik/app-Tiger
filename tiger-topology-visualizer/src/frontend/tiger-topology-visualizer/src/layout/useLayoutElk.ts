///
///
/// Copyright 2021-2026 gematik GmbH
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

import ELK from "elkjs/lib/elk.bundled.js";
import { type Edge, type Node, Position } from "@vue-flow/core";
import type { ElkNode } from "elkjs/lib/elk-api";

type ElkDirectionType = "RIGHT" | "LEFT" | "UP" | "DOWN";
type FlowNode = Node;
type FlowEdge = Edge;

type ElkInputNode = {
  id: string;
  width: number;
  height: number;
  children?: ElkInputNode[];
  layoutOptions?: Record<string, string>;
};

type ElkInputEdge = {
  id: string;
  sources: [string];
  targets: [string];
};

type LayoutResult = {
  nodes: FlowNode[];
  edges: FlowEdge[];
};

const DEFAULT_NODE_WIDTH = 180;
const DEFAULT_NODE_HEIGHT = 64;
const DEFAULT_GROUP_WIDTH = 280;
const DEFAULT_GROUP_HEIGHT = 180;

const elk = new ELK();

/**
 * The vueflow library draws the diagrams, but it has no autolayout.
 * We use the ELK library to calculate the positions of nodes and edges, and then feed those back into vueflow.
 *
 */

export async function layoutWithElk(
  nodes: FlowNode[],
  edges: FlowEdge[],
  direction: ElkDirectionType = "DOWN",
): Promise<LayoutResult> {
  const sanitizedNodes = sanitizeNodes(nodes);
  const nodesById = new Map(sanitizedNodes.map((node) => [node.id, node]));
  const childrenByParent = buildChildrenByParent(sanitizedNodes);
  const elkChildren = (childrenByParent.get(undefined) ?? []).map((node) =>
    buildElkNode(node, childrenByParent),
  );
  const knownNodeIds = new Set(nodesById.keys());
  const safeEdges = sanitizeEdges(edges, knownNodeIds);

  const graph: {
    id: string;
    layoutOptions: Record<string, string>;
    children: ElkInputNode[];
    edges: ElkInputEdge[];
  } = {
    id: "root",
    layoutOptions: {
      "elk.algorithm": "layered",
      "elk.hierarchyHandling": "INCLUDE_CHILDREN",
      "elk.direction": direction,
      "elk.edgeRouting": "ORTHOGONAL",
      "elk.layered.spacing.nodeNodeBetweenLayers": "100",
      "elk.spacing.nodeNode": "80",
      "elk.spacing.edgeNode": "30",
      "elk.padding": "[top=36,left=36,bottom=36,right=36]",
    },
    children: elkChildren,
    edges: safeEdges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
    })),
  };

  try {
    const layoutedGraph = await elk.layout(graph);

    return {
      nodes: flattenToVueFlowNodes(layoutedGraph, nodesById, direction),
      edges: safeEdges,
    };
  } catch {
    return {
      nodes: sanitizedNodes,
      edges: safeEdges,
    };
  }
}

function sanitizeNodes(nodes: FlowNode[]): FlowNode[] {
  const clonedNodes = nodes.map((node) => ({
    ...node,
  }));

  const knownNodeIds = new Set(clonedNodes.map((node) => node.id));
  const parentById = new Map(
    clonedNodes.map((node) => [node.id, node.parentNode]),
  );

  function getSafeParent(nodeId: string): string | undefined {
    const directParent = parentById.get(nodeId);
    if (
      !directParent ||
      !knownNodeIds.has(directParent) ||
      directParent === nodeId
    ) {
      return undefined;
    }

    const visited = new Set<string>([nodeId]);
    let cursor: string | undefined = directParent;
    while (cursor) {
      if (visited.has(cursor)) return undefined;
      visited.add(cursor);

      const next = parentById.get(cursor);
      if (!next || !knownNodeIds.has(next)) return directParent;
      cursor = next;
    }

    return directParent;
  }

  return clonedNodes.map((node) => ({
    ...node,
    parentNode: getSafeParent(node.id),
  }));
}

function buildChildrenByParent(
  nodes: FlowNode[],
): Map<string | undefined, FlowNode[]> {
  const childrenByParent = new Map<string | undefined, FlowNode[]>();

  for (const node of nodes) {
    const key = node.parentNode;
    const siblings = childrenByParent.get(key) ?? [];
    siblings.push(node);
    childrenByParent.set(key, siblings);
  }

  return childrenByParent;
}

function sanitizeEdges(
  edges: FlowEdge[],
  knownNodeIds: Set<string>,
): FlowEdge[] {
  return edges.flatMap((edge) => {
    const source = edge.source;
    const target = edge.target;

    if (!knownNodeIds.has(source) || !knownNodeIds.has(target)) return [];

    return [
      {
        ...edge,
        source,
        target,
      },
    ];
  });
}

function buildElkNode(
  node: FlowNode,
  childrenByParent: Map<string | undefined, FlowNode[]>,
): ElkInputNode {
  const children = childrenByParent.get(node.id) ?? [];
  const isContainer = node.type === "group" || children.length > 0;
  const size = getNodeSize(node, isContainer);

  return {
    id: node.id,
    width: size.width,
    height: size.height,
    children: children.map((child) => buildElkNode(child, childrenByParent)),
    layoutOptions: isContainer
      ? {
          "elk.padding": "[top=44,left=24,bottom=24,right=24]",
          "elk.nodeSize.constraints": "[MINIMUM_SIZE]",
        }
      : undefined,
  };
}

function getNodeSize(
  node: FlowNode,
  isContainer: boolean,
): { width: number; height: number } {
  const label = getNodeLabel(node);
  const labelWidth = estimateLabelWidthPx(label);
  const minWidth = isContainer ? DEFAULT_GROUP_WIDTH : DEFAULT_NODE_WIDTH;
  const minHeight = isContainer ? DEFAULT_GROUP_HEIGHT : DEFAULT_NODE_HEIGHT;
  const existingWidth = typeof node.width === "number" ? node.width : 0;
  const existingHeight = typeof node.height === "number" ? node.height : 0;

  return {
    width: Math.max(existingWidth, labelWidth, minWidth),
    height: Math.max(existingHeight, minHeight),
  };
}

function getNodeLabel(node: FlowNode): string | undefined {
  const label = (node.data as { label?: unknown } | undefined)?.label;
  return typeof label === "string" && label.trim().length > 0
    ? label.trim()
    : undefined;
}

function estimateLabelWidthPx(label: string | undefined): number {
  const CHAR_WIDTH_PX = 8;
  const HORIZONTAL_PADDING_PX = 48;
  return label ? label.length * CHAR_WIDTH_PX + HORIZONTAL_PADDING_PX : 0;
}

function flattenToVueFlowNodes(
  graph: ElkNode,
  nodesById: Map<string, FlowNode>,
  direction: ElkDirectionType,
): FlowNode[] {
  const result: FlowNode[] = [];

  function visit(
    node: ElkNode,
    parentNode?: string,
    parentAbsX = 0,
    parentAbsY = 0,
  ): void {
    const original = nodesById.get(node.id);
    if (!original) return;

    const selfX = node.x ?? 0;
    const selfY = node.y ?? 0;
    const absoluteX = parentAbsX + selfX;
    const absoluteY = parentAbsY + selfY;

    result.push({
      ...original,
      parentNode,
      width: node.width ?? original.width,
      height: node.height ?? original.height,
      targetPosition: targetPosition(direction),
      sourcePosition: sourcePosition(direction),
      position: parentNode
        ? { x: selfX, y: selfY }
        : { x: absoluteX, y: absoluteY },
    });

    for (const child of node.children ?? []) {
      visit(child, node.id, absoluteX, absoluteY);
    }
  }

  for (const child of graph.children ?? []) {
    visit(child);
  }

  return result;
}

function targetPosition(direction: ElkDirectionType): Position {
  return elkDirectionToFlowPosition(invert(direction));
}

function sourcePosition(direction: ElkDirectionType): Position {
  return elkDirectionToFlowPosition(direction);
}

function invert(direction: ElkDirectionType): ElkDirectionType {
  switch (direction) {
    case "LEFT":
      return "RIGHT";
    case "RIGHT":
      return "LEFT";
    case "DOWN":
      return "UP";
    case "UP":
      return "DOWN";
  }
}

function elkDirectionToFlowPosition(direction: ElkDirectionType): Position {
  switch (direction) {
    case "LEFT":
      return Position.Left;
    case "RIGHT":
      return Position.Right;
    case "DOWN":
      return Position.Bottom;
    case "UP":
      return Position.Top;
  }
}
