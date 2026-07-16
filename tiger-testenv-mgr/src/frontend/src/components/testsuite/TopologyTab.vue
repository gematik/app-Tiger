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
  type AdditionalEdge,
  TopologyDiagram,
  useDiagramModel,
} from "tiger-topology-visualizer";
import { computed, onMounted, watchEffect } from "vue";
import { useFeaturesStore } from "@/stores/features.ts";
import { storeToRefs } from "pinia";
import { initiallyCapturedByProxy } from "@/types/rbel/MessageMetaDataDto.ts";
import type MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto.ts";

const baseURL = import.meta.env.BASE_URL;

const topologyStore = useDiagramModel();
const { rbelMetadata } = storeToRefs(useFeaturesStore());

onMounted(() => {
  loadTopology();
});

function loadTopology() {
  topologyStore.loadFromLiveEndpoint(baseURL + "topology");
}

const additionalEdges = computed(() => convertToSetOfEdges(rbelMetadata.value));

watchEffect(() => topologyStore.addAdditionalEdges(additionalEdges.value));

function convertToSetOfEdges(metadata: MessageMetaDataDto[]): AdditionalEdge[] {
  return metadata.map((m) => ({
    sender: m.bundledServerNameSender,
    receiver: m.bundledServerNameReceiver,
    proxiedVia: initiallyCapturedByProxy(m),
  }));
}
</script>

<template>
  <TopologyDiagram :show-filtering="true"></TopologyDiagram>
</template>

<style scoped></style>
