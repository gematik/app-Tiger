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
import { ref, watchEffect } from "vue";
import { useDiagramModel } from "../stores/diagramModel.ts";
import { Background } from "@vue-flow/background";
import { layoutWithElk } from "../layout/useLayoutElk.ts";
import { MiniMap } from "@vue-flow/minimap";
import Message from "primevue/message";

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
</script>

<template>
  <div v-if="layoutError" class="layout-error">
    <Message severity="error">
      Auto‑layout failed while processing the elements of the diagram:
      {{ layoutError }}
    </Message>
  </div>
  <VueFlow v-else :nodes="nodes" :edges="edges">
    <Background></Background>
    <MiniMap pannable zoomable></MiniMap>
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
:deep(.vue-flow__node.parent) {
  background: transparent !important;
  border: 2px solid #999;
  pointer-events: none;
  z-index: 0;
}
</style>
