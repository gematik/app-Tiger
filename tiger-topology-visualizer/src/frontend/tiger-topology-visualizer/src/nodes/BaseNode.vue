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

<!--
  BaseNode: Reusable wrapper for custom VueFlow nodes.
  Provides a consistent look, source/target handles, and a Popover
  that displays the node's config as syntax-highlighted YAML.

  Usage in a concrete node:
    <BaseNode :nodeData="data" :sourcePosition="Position.Right" :targetPosition="Position.Left">
      <template #icon>🔒</template>  (optional leading icon)
    </BaseNode>
-->

<script setup lang="ts">
import Popover from "primevue/popover";
import Card from "primevue/card";
import { ref, computed } from "vue";
import "highlight.js/styles/github.css";
import hljs from "highlight.js/lib/core";
import yaml from "highlight.js/lib/languages/yaml";
hljs.registerLanguage("yaml", yaml);
import hljsVuePlugin from "@highlightjs/vue-plugin";
import { Handle, Position } from "@vue-flow/core";
import { stringify } from "yaml";
const HighlightJs = hljsVuePlugin.component;

const props = defineProps<{
  nodeData: Record<string, any>;
  sourcePosition?: Position;
  targetPosition?: Position;
  transparent?: boolean;
}>();

const hasContent = computed(
  () => props.nodeData.config && Object.keys(props.nodeData.config).length > 0,
);

const popoverRef = ref();

function toggle(event: Event) {
  if (hasContent.value) {
    popoverRef.value.toggle(event);
  }
}
</script>

<template>
  <div class="base-node" :class="{ 'base-node--transparent': transparent }">
    <!-- Node body -->
    <div class="base-node-body">
      <span v-if="$slots.icon" class="base-node-icon"
        ><slot name="icon"
      /></span>
      <span class="base-node-label">{{ nodeData.label }}</span>
    </div>
    <button v-if="hasContent" class="detail-btn" @click="toggle" title="Show details">
      ℹ
    </button>

    <Popover v-if="hasContent" ref="popoverRef">
      <Card>
        <template #content>
          <div class="detail-grid">
            <HighlightJs
              :autodetect="false"
              language="yaml"
              :code="stringify(nodeData.config, null, 2)"
            />
          </div>
        </template>
      </Card>
    </Popover>

    <Handle type="target" :position="targetPosition" />
    <Handle type="source" :position="sourcePosition" />
  </div>
</template>

<style scoped>
.base-node {
  position: relative;
  padding: 10px 32px 10px 16px;
  border-radius: 8px;
  background: #fff;
  border: 2px solid #94a3b8;
  font-family: monospace;
  min-width: 120px;
  text-align: center;
  height: 100%;
}

.base-node-body {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

.detail-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  background: #fff;
  border: 1px solid #cbd5e1;
  cursor: pointer;
  color: #64748b;
  font-size: 14px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s, background 0.2s;
  line-height: 1;
}

.detail-btn:hover {
  color: #0f172a;
  background: #f1f5f9;
}

.base-node--transparent {
  background: transparent;
  border-style: dashed;
}
</style>
