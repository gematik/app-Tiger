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
import { useVueFlow, VueFlow } from "@vue-flow/core";
import { computed, markRaw, nextTick, ref, watch } from "vue";
import { useDiagramModel } from "../stores/diagramModel.ts";
import { Background } from "@vue-flow/background";
import {
  type ElkDirectionType,
  type ElkLayoutModeType,
  layoutWithElk,
} from "../layout/useLayoutElk.ts";
import { MiniMap } from "@vue-flow/minimap";
import { Controls, ControlButton } from "@vue-flow/controls";
import "@vue-flow/controls/dist/style.css";
import Button from "primevue/button";
import ButtonGroup from "primevue/buttongroup";
import Message from "primevue/message";
import DefaultNode from "../nodes/DefaultNode.vue";
import GroupNode from "../nodes/GroupNode.vue";
import TigerProxyNode from "../nodes/TigerProxyNode.vue";
import ExternalJarNode from "../nodes/ExternalJarNode.vue";
import ExternalUrlNode from "../nodes/ExternalUrlNode.vue";
import ZionNode from "../nodes/ZionNode.vue";
import DockerNode from "../nodes/DockerNode.vue";
import ComposeNode from "../nodes/ComposeNode.vue";
import HttpbinNode from "../nodes/HttpbinNode.vue";
import RouteNode from "../nodes/RouteNode.vue";
import CustomEdge from "../edges/CustomEdge.vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { fas } from "@fortawesome/free-solid-svg-icons";
import { useLabelLayoutStore } from "../stores/labelLayoutStore";
import TigerProxyExternalNode from "../nodes/TigerProxyExternalNode.vue";
import TopologyLegend from "./TopologyLegend.vue";

const props = withDefaults(
  defineProps<{
    showFiltering?: boolean;
  }>(),
  {
    showFiltering: true,
  },
);

const diagramModel = useDiagramModel();
const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);
const layoutError = ref<string | null>(null);
const direction = ref<ElkDirectionType>("DOWN");
const layoutMode = ref<ElkLayoutModeType>("auto");
const { fitView } = useVueFlow();
const labelLayoutStore = useLabelLayoutStore();

function toggleDirection() {
  direction.value = direction.value === "DOWN" ? "RIGHT" : "DOWN";
}

function setLayoutMode(mode: ElkLayoutModeType) {
  layoutMode.value = mode;
}

async function runLayout() {
  try {
    const layoutResult = await layoutWithElk(
      diagramModel.filteredNodes,
      diagramModel.filteredEdges,
      direction.value,
      layoutMode.value,
    );
    labelLayoutStore.clearAll();
    nodes.value = layoutResult.nodes;
    edges.value = layoutResult.edges;
    layoutError.value = null;
    applyDynamicHandles();
    await nextTick(() => {
      fitView();
    });
  } catch (error) {
    layoutError.value =
      error instanceof Error ? error.message : "Layout failed";
  }
}

/* Handles shall be on the side closes to where the edge arrives at node.
This is to avoid having weird edges when circular connections are displayed
 */
function applyDynamicHandles() {
  const nodeMap = new Map(nodes.value.map((node) => [node.id, node]));

  edges.value = edges.value.map((edge) => {
    const source = nodeMap.get(edge.source);
    const target = nodeMap.get(edge.target);

    if (!source || !target) return edge;

    const sourceSide = getClosestSide(source, target, nodeMap);
    const targetSide = getClosestSide(target, source, nodeMap);

    return {
      ...edge,
      sourceHandle: `source-${sourceSide}`,
      targetHandle: `target-${targetSide}`,
    };
  });
}

function getClosestSide(
  fromNode: any,
  toNode: any,
  nodeMap: Map<string, any>,
): "top" | "right" | "bottom" | "left" {
  const fromCenter = getAbsoluteNodeCenter(fromNode, nodeMap);
  const toCenter = getAbsoluteNodeCenter(toNode, nodeMap);

  const deltaX = toCenter.x - fromCenter.x;
  const deltaY = toCenter.y - fromCenter.y;
  const absDeltaX = Math.abs(deltaX);
  const absDeltaY = Math.abs(deltaY);

  if (absDeltaX > absDeltaY) {
    return deltaX > 0 ? "right" : "left";
  } else {
    return deltaY > 0 ? "bottom" : "top";
  }
}

function getAbsoluteNodeCenter(
  node: any,
  nodeMap: Map<string, any>,
): { x: number; y: number } {
  let absoluteX = node.position?.x ?? 0;
  let absoluteY = node.position?.y ?? 0;
  let parentId = node.parentNode;

  while (parentId) {
    const parent = nodeMap.get(parentId);
    if (!parent) break;
    absoluteX += parent.position?.x ?? 0;
    absoluteY += parent.position?.y ?? 0;
    parentId = parent.parentNode;
  }

  return {
    x: absoluteX + (node.width ?? 180) / 2,
    y: absoluteY + (node.height ?? 64) / 2,
  };
}

watch(
  [
    () => diagramModel.filteredNodes,
    () => diagramModel.filteredEdges,
    direction,
    layoutMode,
  ],
  runLayout,
  {
    immediate: true,
  },
);

watch(
  () =>
    //Watching only position changes of the nodes
    nodes.value.map((n) => ({ id: n.id, x: n.position?.x, y: n.position?.y })),
  () => {
    applyDynamicHandles();
  },
  { deep: true },
);

const nodeTypes: Record<string, any> = {
  default: markRaw(DefaultNode),
  group: markRaw(GroupNode),
  tigerProxy: markRaw(TigerProxyNode),
  tigerProxyExternal: markRaw(TigerProxyExternalNode),
  externalJar: markRaw(ExternalJarNode),
  externalUrl: markRaw(ExternalUrlNode),
  docker: markRaw(DockerNode),
  composeService: markRaw(ComposeNode),
  zion: markRaw(ZionNode),
  httpbin: markRaw(HttpbinNode),
  route: markRaw(RouteNode),
};

const edgeTypes: Record<string, any> = {
  default: markRaw(CustomEdge),
};

const legendNodeTypes = computed(() =>
  [...new Set(nodes.value.map((node) => node.type ?? "default"))].sort(
    (left, right) => left.localeCompare(right),
  ),
);
</script>

<template>
  <div v-if="layoutError" class="layout-error">
    <Message severity="error">
      Auto‑layout failed while processing the elements of the diagram:
      {{ layoutError }}
    </Message>
  </div>
  <div class="container-for-vueflow" v-else>
    <div v-if="props.showFiltering" class="edge-toggle-controls">
      <ButtonGroup>
        <Button
          :severity="
            diagramModel.edgeDisplayMode === 'ALL' ? 'primary' : 'secondary'
          "
          size="small"
          label="All Edges"
          @click="diagramModel.edgeDisplayMode = 'ALL'"
        ></Button>
        <Button
          :severity="
            diagramModel.edgeDisplayMode === 'LIVE' ? 'primary' : 'secondary'
          "
          size="small"
          label="Only Live Data"
          @click="diagramModel.edgeDisplayMode = 'LIVE'"
        ></Button>
        <Button
          :severity="
            diagramModel.edgeDisplayMode === 'STATIC' ? 'primary' : 'secondary'
          "
          size="small"
          label="Only Static Data"
          @click="diagramModel.edgeDisplayMode = 'STATIC'"
        ></Button>
      </ButtonGroup>
    </div>
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :nodeTypes="nodeTypes"
      :edgeTypes="edgeTypes"
    >
      <Background></Background>
      <div class="diagram-controls">
        <MiniMap pannable zoomable></MiniMap>
        <Controls>
          <ControlButton
            @click="setLayoutMode('auto')"
            title="Auto choose layout"
          >
            <FontAwesomeIcon
              :icon="fas.faWandMagicSparkles"
              :style="{ opacity: layoutMode === 'auto' ? 1 : 0.55 }"
            />
          </ControlButton>
          <ControlButton
            @click="setLayoutMode('compact')"
            title="Apply compact layout"
          >
            <FontAwesomeIcon
              :icon="fas.faCompress"
              :style="{ opacity: layoutMode === 'compact' ? 1 : 0.55 }"
            />
          </ControlButton>
          <ControlButton
            @click="setLayoutMode('spread')"
            title="Apply spread layout"
          >
            <FontAwesomeIcon
              :icon="fas.faMaximize"
              :style="{ opacity: layoutMode === 'spread' ? 1 : 0.55 }"
            />
          </ControlButton>
          <ControlButton
            @click="toggleDirection"
            title="Toggle layout direction"
          >
            {{ direction === "DOWN" ? "↓" : "→" }}
          </ControlButton>
        </Controls>
      </div>

      <TopologyLegend :node-types="legendNodeTypes" />
    </VueFlow>
  </div>
</template>

<style scoped>
.container-for-vueflow {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.vue-flow {
  flex: 1;
  min-height: 0;
  font-family: monospace;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

.edge-toggle-controls {
  padding-block-start: 0.5rem;
  padding-block-end: 0.5rem;
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

/* Make group nodes transparent so edges show through */
:deep(.vue-flow__node.parent),
:deep(.vue-flow__node-group) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 0 !important;
  pointer-events: none;
  z-index: 0;
}

/* Strip VueFlow's default node style — BaseNode handles all styling */
:deep(.vue-flow__node-default) {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}

:deep(.vue-flow__node-default.selectable:hover),
:deep(.vue-flow__node-input.selectable:hover),
:deep(.vue-flow__node-output.selectable:hover) {
  box-shadow: none;
}

:deep(.vue-flow__arrowhead polyline) {
  stroke: black !important;
  fill: black !important;
}

:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke-width: 3;
}

:deep(.vue-flow__edge:hover .vue-flow__edge-path) {
  stroke-width: 2;
}

:deep(.tiger-edge-trafficSubscription) {
  stroke: #ffc81e;
  stroke-dasharray: 6 3;
}

:deep(.tiger-edge-proxyToRoute),
:deep(.tiger-edge-routeToTarget),
:deep(.tiger-edge-makeBackendRequest),
:deep(.tiger-edge-directReverseRoute),
:deep(.tiger-edge-usesProxy),
:deep(.tiger-edge-implicitRoute),
:deep(.tiger-edge-proxyAndRoute) {
  stroke: black;
}

:deep(.tiger-edge-dynamicTraffic) {
  stroke: #f6a337;
  stroke-width: 2.5;
  stroke-dasharray: 10 7;
  animation: tiger-edge-bidirectional-flow 3.2s ease-in-out infinite alternate;
}

@keyframes tiger-edge-bidirectional-flow {
  from {
    stroke-dashoffset: -24;
  }
  to {
    stroke-dashoffset: 24;
  }
}

.diagram-controls {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
}

/* Reset absolute positioning so children participate in flexbox */
.diagram-controls :deep(.vue-flow__minimap),
.diagram-controls :deep(.vue-flow__controls) {
  position: relative !important;
  top: auto !important;
  left: auto !important;
  right: auto !important;
  bottom: auto !important;
}
</style>
