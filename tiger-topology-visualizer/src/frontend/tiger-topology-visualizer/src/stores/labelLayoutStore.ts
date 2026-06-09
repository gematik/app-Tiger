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

import { defineStore } from "pinia";
import { ref } from "vue";

export interface LabelPosition {
  x: number;
  y: number;
}

export interface LabelSize {
  width: number;
  height: number;
}

export interface LabelRect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface EdgeLabelLayout {
  position: LabelPosition;
  rect: LabelRect;
}

const CANDIDATE_OFFSETS: ReadonlyArray<Readonly<LabelPosition>> = [
  { x: 0, y: 0 },
  { x: 0, y: -22 },
  { x: 0, y: 22 },
  { x: 22, y: 0 },
  { x: -22, y: 0 },
  { x: 22, y: -22 },
  { x: -22, y: -22 },
  { x: 22, y: 22 },
  { x: -22, y: 22 },
  { x: 44, y: 0 },
  { x: -44, y: 0 },
  { x: 0, y: -44 },
  { x: 0, y: 44 },
];

function intersects(a: LabelRect, b: LabelRect): boolean {
  return !(
    a.right <= b.left ||
    a.left >= b.right ||
    a.bottom <= b.top ||
    a.top >= b.bottom
  );
}

function rectangleContainingLabel(
  position: LabelPosition,
  size: LabelSize,
): LabelRect {
  return {
    left: position.x - size.width / 2,
    right: position.x + size.width / 2,
    top: position.y - size.height / 2,
    bottom: position.y + size.height / 2,
  };
}

/**
 * Store to keep track all the label positions and rects so that we can calculate non overlapping positions
 */
export const useLabelLayoutStore = defineStore("labelLayout", () => {
  const layoutsByEdgeId = ref<Record<string, EdgeLabelLayout>>({});

  function resolveCollisionFreePosition(
    edgeId: string,
    basePosition: LabelPosition,
    size: LabelSize,
  ): LabelPosition {
    const existingLabels = Object.entries(layoutsByEdgeId.value).filter(
      ([id]) => id !== edgeId,
    );

    //searches for the first candidate_offset that avoids collision.
    //if none is found, no change is made.
    for (const offset of CANDIDATE_OFFSETS) {
      const candidate = {
        x: basePosition.x + offset.x,
        y: basePosition.y + offset.y,
      };

      const candidateRect = rectangleContainingLabel(candidate, size);
      const collides = existingLabels.some(([, layout]) =>
        intersects(candidateRect, layout.rect),
      );

      if (!collides) {
        return candidate;
      }
    }

    return basePosition;
  }

  function registerOrUpdateLabelRect(
    edgeId: string,
    position: LabelPosition,
    size: LabelSize,
  ): void {
    layoutsByEdgeId.value[edgeId] = {
      position,
      rect: rectangleContainingLabel(position, size),
    };
  }

  function releaseEdge(edgeId: string): void {
    delete layoutsByEdgeId.value[edgeId];
  }

  function clearAll(): void {
    layoutsByEdgeId.value = {};
  }

  return {
    layoutsByEdgeId,
    resolveCollisionFreePosition,
    registerOrUpdateLabelRect,
    releaseEdge,
    clearAll,
  };
});
