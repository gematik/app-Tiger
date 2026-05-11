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
import { VueFlow } from "@vue-flow/core";
import { markRaw, ref, watchEffect } from "vue";
import { useDiagramModel } from "../stores/diagramModel.ts";
import { Background } from "@vue-flow/background";
import { layoutWithElk } from "../layout/useLayoutElk.ts";
import { MiniMap } from "@vue-flow/minimap";
import Message from "primevue/message";
import DefaultNode from "../nodes/DefaultNode.vue";
import GroupNode from "../nodes/GroupNode.vue";

const diagramModel = useDiagramModel();
const nodes = ref<any[]>([]);
const edges = ref<any[]>([]);
const layoutError = ref<string | null>(null);

watchEffect(async () => {
  try {
    const layoutResult = await layoutWithElk(
      diagramModel.model.nodes,
      diagramModel.model.edges,
    );
    nodes.value = layoutResult.nodes;
    edges.value = layoutResult.edges;
    layoutError.value = null;
  } catch (error) {
    layoutError.value =
      error instanceof Error ? error.message : "Layout failed";
  }
});

const nodeTypes: Record<string, any> = {
  default: markRaw(DefaultNode),
};
</script>

<template>
  <div v-if="layoutError" class="layout-error">
    <Message severity="error">
      Auto‑layout failed while processing the elements of the diagram:
      {{ layoutError }}
    </Message>
  </div>
  <VueFlow v-else :nodes="nodes" :edges="edges" :nodeTypes="nodeTypes">
    <Background></Background>
    <MiniMap pannable zoomable></MiniMap>
    <template #node-group="props">
      <GroupNode :id="props.id" :data="props.data" />
    </template>
  </VueFlow>
</template>

<style scoped>
.vue-flow {
  height: 100%;
  width: 100%;
  font-family: monospace;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
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
</style>
