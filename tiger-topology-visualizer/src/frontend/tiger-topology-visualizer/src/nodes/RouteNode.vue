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
import { getNodeVisual } from "./nodeVisuals";

defineProps<{
  id: string;
  data: Record<string, any>;
  sourcePosition?: Position;
  targetPosition?: Position;
}>();

const routeVisual = getNodeVisual("route");
</script>

<template>
  <BaseNode
    :style="{
      background: routeVisual.colors.background,
      borderColor: routeVisual.colors.border,
      borderStyle: routeVisual.colors.borderStyle,
      color: routeVisual.colors.text,
    }"
    :nodeData="data"
    :sourcePosition="sourcePosition"
    :targetPosition="targetPosition"
  >
    <template #icon>
      <FontAwesomeIcon :icon="routeVisual.icon!" size="lg" />
    </template>
    <template #content>
      <div class="d-flex flex-column gap-1">
        <div><em>Route</em></div>
        <div><b>from:</b> {{ data.config.from }}</div>
      </div>
    </template>
  </BaseNode>
</template>
