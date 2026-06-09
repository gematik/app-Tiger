<!--
  - Copyright 2021-2026 gematik GmbH
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -
  - *******
  -
  - For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
  -
  -->

<script setup lang="ts">
import {
  BaseEdge,
  EdgeLabelRenderer,
  type EdgeMouseEvent,
  type EdgeProps,
  getBezierPath,
  useVueFlow,
} from "@vue-flow/core";
import { computed, onBeforeUnmount, ref, watch, watchEffect } from "vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { fas } from "@fortawesome/free-solid-svg-icons";
import { useDiagramModel } from "../stores/diagramModel";
import {
  useLabelLayoutStore,
  type LabelSize,
} from "../stores/labelLayoutStore";

function toFiniteNumber(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function estimateLabelSize(
  labelText: string,
  hasProxyIcon: boolean,
): LabelSize {
  const textWidth = Math.max(36, labelText.length * 9);
  const iconWidth = hasProxyIcon ? 20 : 0;
  return {
    width: textWidth + iconWidth + 12,
    height: 24,
  };
}

const props = defineProps<EdgeProps>();
const labelLayoutStore = useLabelLayoutStore();

const path = computed(() => getBezierPath(props));
const labelPosition = ref({ x: 0, y: 0 });

watch(
  () =>
    [
      props.id,
      path.value[1],
      path.value[2],
      String(props.label ?? ""),
      Boolean(props.data?.proxiedVia),
    ] as const,
  ([edgeId, rawX, rawY, labelText, hasProxyIcon]) => {
    const baseX = toFiniteNumber(rawX);
    const baseY = toFiniteNumber(rawY);
    const trimmedLabel = labelText.trim();

    // Labels without text are intentionally excluded from collision tracking.
    if (!trimmedLabel) {
      labelLayoutStore.releaseEdge(edgeId);
      labelPosition.value = { x: baseX, y: baseY };
      return;
    }

    const size = estimateLabelSize(trimmedLabel, hasProxyIcon);
    const resolved = labelLayoutStore.resolveCollisionFreePosition(
      edgeId,
      { x: baseX, y: baseY },
      size,
    );

    labelPosition.value = resolved;
    labelLayoutStore.registerOrUpdateLabelRect(edgeId, resolved, size);
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  labelLayoutStore.releaseEdge(props.id);
});

const { onEdgeMouseEnter, onEdgeMouseLeave, findNode } = useVueFlow();

const hovered = ref<boolean>(false);

const highlightProxy = computed(
  () => props.data?.proxiedVia && (props.selected || hovered.value),
);

const proxyNodeCenter = computed(() => {
  const node = findNode(props.data?.proxiedVia);
  if (!node) return null;
  return {
    x: node.position.x + (node.dimensions?.width ?? 0) / 2,
    y: node.position.y + (node.dimensions?.height ?? 0) / 2,
  };
});

onEdgeMouseEnter((event: EdgeMouseEvent) => {
  hovered.value = event.edge.id == props.id;
});

onEdgeMouseLeave(() => {
  hovered.value = false;
});

watchEffect(() => {
  const proxiedVia = props.data?.proxiedVia;
  if (!proxiedVia) return;
  if (highlightProxy.value) {
    useDiagramModel().highlightNode(proxiedVia);
  } else {
    useDiagramModel().clearHighlight(proxiedVia);
  }
});
</script>

<template>
  <BaseEdge
    :class="`tiger-edge tiger-edge-${type}`"
    :id="id"
    :style="style"
    :path="path[0]"
    :marker-end="markerEnd"
    :marker-start="markerStart"
  />

  <line
    v-if="highlightProxy && proxyNodeCenter"
    :x1="path[1]"
    :y1="path[2]"
    :x2="proxyNodeCenter.x"
    :y2="proxyNodeCenter.y"
    stroke="#f97316"
    stroke-width="2"
    stroke-dasharray="6 3"
    stroke-opacity="0.6"
  />
  <EdgeLabelRenderer>
    <!-- For full control over the label, we need to get out of the SVG and do it in a div.
    See https://vueflow.dev/examples/edges/#edges EdgeWithButton.vue example -->
    <div
      :style="{
        background: 'white',
        pointerEvents: 'all',
        position: 'absolute',
        transform: `translate(-50%, -50%) translate(${labelPosition.x}px,${labelPosition.y}px)`,
      }"
      @mouseenter="hovered = true"
      @mouseleave="hovered = false"
    >
      <FontAwesomeIcon
        v-if="data.proxiedVia"
        :style="{ color: '#ffa55c' }"
        :icon="fas.faProjectDiagram"
      />
      {{ label }}
    </div>
  </EdgeLabelRenderer>
</template>
