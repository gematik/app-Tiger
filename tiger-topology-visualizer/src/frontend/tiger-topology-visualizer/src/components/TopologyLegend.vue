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
import { computed, ref } from "vue";
import { Panel } from "@vue-flow/core";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { getNodeVisual } from "../nodes/nodeVisuals";

type LegendNodeItem = {
  type: string;
  visual: ReturnType<typeof getNodeVisual>;
};

const props = defineProps<{
  nodeTypes: string[];
}>();

const legendExpanded = ref(false);

const legendNodeItems = computed<LegendNodeItem[]>(() => {
  return props.nodeTypes.slice().map((type) => {
    const visual = getNodeVisual(type);
    return {
      type,
      visual,
    };
  });
});
</script>

<template>
  <Panel position="bottom-left" class="diagram-legend-panel">
    <div class="diagram-legend nopan nodrag nowheel">
      <button
        class="legend-toggle"
        type="button"
        :aria-expanded="legendExpanded"
        @click="legendExpanded = !legendExpanded"
      >
        {{ legendExpanded ? "Legend -" : "Legend +" }}
      </button>

      <div v-if="legendExpanded" class="legend-content">
        <div class="legend-section" v-if="legendNodeItems.length">
          <h4>Nodes</h4>
          <div
            class="legend-node-row"
            v-for="item in legendNodeItems"
            :key="`node-${item.type}`"
          >
            <div
              class="legend-node-chip"
              :style="{
                background: item.visual.colors.background,
                borderColor: item.visual.colors.border,
                borderStyle: item.visual.colors.borderStyle ?? 'solid',
                color: item.visual.colors.text,
              }"
            >
              <FontAwesomeIcon
                v-if="item.visual.icon"
                :icon="item.visual.icon!"
              />
              <span>{{ item.visual.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Panel>
</template>

<style scoped>
.diagram-legend-panel {
  pointer-events: none;
}

.diagram-legend {
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.legend-toggle {
  border: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font-family: monospace;
  font-size: 12px;
}

.legend-content {
  margin-top: 8px;
  min-width: 280px;
  max-width: 320px;
  max-height: 45vh;
  overflow-y: auto;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  padding: 10px;
  text-align: left;
}

.legend-section h4 {
  margin: 0 0 8px;
  font-size: 12px;
  text-transform: uppercase;
  color: #475569;
}

.legend-section + .legend-section {
  margin-top: 12px;
}

.legend-node-row,
.legend-edge-row {
  margin-bottom: 8px;
}

.legend-node-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid;
  border-radius: 8px;
  padding: 6px 10px;
  min-width: 170px;
}

.legend-node-chip span {
  font-size: 12px;
}
</style>
