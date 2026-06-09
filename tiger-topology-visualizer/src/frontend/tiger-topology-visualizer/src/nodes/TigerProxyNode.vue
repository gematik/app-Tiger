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
import { Position } from "@vue-flow/core";
import BaseNode from "./BaseNode.vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { computed } from "vue";
import { getNodeVisual } from "./nodeVisuals";

const props = defineProps<{
  id: string;
  data: Record<string, any>;
  sourcePosition?: Position;
  targetPosition?: Position;
}>();

const proxyType = computed(() =>
  props.data?.config?.tigerProxyConfiguration?.directReverseProxy?.hostname
    ? "Reverse"
    : "Forward",
);

const tigerProxyVisual = getNodeVisual("tigerProxy");
</script>

<template>
  <BaseNode
    :style="{
      background: tigerProxyVisual.colors.background,
      borderColor: tigerProxyVisual.colors.border,
      borderStyle: tigerProxyVisual.colors.borderStyle,
      color: tigerProxyVisual.colors.text,
    }"
    :nodeData="data"
    :sourcePosition="sourcePosition"
    :targetPosition="targetPosition"
  >
    <template #icon>
      <FontAwesomeIcon :icon="tigerProxyVisual.icon!" size="lg" />
    </template>
    <template #content>
      <div class="d-flex flex-column gap-1">
        <div>{{ data.label }}</div>
        <div class="proxy-type">
          <em>&lt;&lt;{{ proxyType }}&gt;&gt;</em>
        </div>
      </div>
    </template>
  </BaseNode>
</template>

<style scoped>
.proxy-type em {
  font-size: smaller !important;
}
</style>
