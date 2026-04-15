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
  position: { x: number; y: number };
}

export interface DiagramEdgeDto {
  id: string;
  source: string;
  target: string;
}

export interface ConfigurationDiagramDto {
  nodes: DiagramNodeDto[];
  edges: DiagramEdgeDto[];
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
