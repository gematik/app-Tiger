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

export interface DiagramNodeDto {
  id: string;
  type: string;
  data: Record<string, any>;
  parentNode: string | undefined;
  expandParent: boolean | undefined;
  position: { x: number; y: number };
}

export interface DiagramEdgeDto {
  id: string;
  type: string;
  source: string;
  target: string;
  label: string | undefined;
  markerEnd: string;
  markerStart: string | undefined;
  data: Record<string, any>;
}

export interface ConfigurationDiagramDto {
  nodes: DiagramNodeDto[];
  edges: DiagramEdgeDto[];
  warnings: string[];
}

export function createDiagramNodeDto(
  input: Omit<DiagramNodeDto, "position"> & {
    position?: { x: number; y: number };
  },
): DiagramNodeDto {
  return {
    ...input,
    position: input.position ?? { x: 0, y: 0 },
  };
}

function collapseBidirectionalDynamicTrafficEdges(
  edges: DiagramEdgeDto[],
): DiagramEdgeDto[] {
  const result: DiagramEdgeDto[] = [];
  // Map from canonical pair key (sorted endpoints) to the index of the kept edge in result
  const keptDynamicTrafficEdgeIndexByEndpoints = new Map<string, number>();

  for (const edge of edges) {
    if (edge.type !== "dynamicTraffic") {
      result.push({ ...edge });
      continue;
    }

    const canonicalKey = [edge.source, edge.target]
      .sort((a, b) => a.localeCompare(b))
      .join("--");
    const existingEdgeIndex =
      keptDynamicTrafficEdgeIndexByEndpoints.get(canonicalKey);

    if (existingEdgeIndex === undefined) {
      keptDynamicTrafficEdgeIndexByEndpoints.set(canonicalKey, result.length);
      result.push({ ...edge });
    } else {
      // Reverse direction already represented — ensure the kept edge has markers on both ends.
      const keptEdge = result[existingEdgeIndex];
      keptEdge.markerStart =
        keptEdge.markerStart ?? edge.markerEnd ?? keptEdge.markerEnd;
    }
  }

  return result;
}

export function mergeDiagramModels(
  current: ConfigurationDiagramDto,
  additionalData: ConfigurationDiagramDto,
): ConfigurationDiagramDto {
  const keepFirstById = <T extends { id: string }>(items: T[]): T[] => {
    const seenIds = new Set<string>();
    return items.filter((item) => {
      if (seenIds.has(item.id)) {
        return false;
      }
      seenIds.add(item.id);
      return true;
    });
  };

  // Keep first occurrence and treat duplicate nodes/edges by id only.
  const nodes = keepFirstById([...current.nodes, ...additionalData.nodes]);
  const deduplicatedEdges = keepFirstById([
    ...current.edges,
    ...additionalData.edges,
  ]);
  // Collapse bidirectional dynamicTraffic edges: if A→B and B→A both exist,
  // keep only one edge and mark both ends with arrows.
  const edges = collapseBidirectionalDynamicTrafficEdges(deduplicatedEdges);
  const warnings = Array.from(
    new Set([...current.warnings, ...additionalData.warnings]),
  );

  return {
    nodes,
    edges,
    warnings,
  };
}
