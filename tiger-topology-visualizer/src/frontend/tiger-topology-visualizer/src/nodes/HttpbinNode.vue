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
import {
  FontAwesomeIcon,
  FontAwesomeLayers,
  FontAwesomeLayersText,
} from "@fortawesome/vue-fontawesome";
import { fas } from "@fortawesome/free-solid-svg-icons";
import { getNodeVisual } from "./nodeVisuals";

defineProps<{
  id: string;
  data: Record<string, any>;
  sourcePosition?: Position;
  targetPosition?: Position;
}>();

const visual = getNodeVisual("httpbin");
</script>

<template>
  <BaseNode
    :style="{
      background: visual.colors.background,
      borderColor: visual.colors.border,
      borderStyle: visual.colors.borderStyle,
      color: visual.colors.text,
    }"
    :nodeData="data"
    :sourcePosition="sourcePosition"
    :targetPosition="targetPosition"
  >
    <template #icon>
      <FontAwesomeLayers :fixed-width="true" size="lg">
        <FontAwesomeIcon :icon="fas.faTrash" />
        <FontAwesomeLayersText
          class="fa-inverse"
          value="HT"
          transform="shrink-9 up-0.5"
        />
        <FontAwesomeLayersText
          class="fa-inverse"
          value="TP"
          transform="shrink-9 down-5"
        />
      </FontAwesomeLayers>
    </template>
  </BaseNode>
</template>

<style></style>
