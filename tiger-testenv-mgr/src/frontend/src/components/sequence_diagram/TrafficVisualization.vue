<!--


    Copyright 2021-2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<template>
  <div
    id="visualization_pane"
    class="tab-pane execution-pane-tabs"
    role="tabpanel"
  >
    <sequence-diagram
      :diagram-step-description="diagramSteps"
      @click-on-message-with-sequence-number="handleClickOnSequenceNumber"
    ></sequence-diagram>
  </div>
</template>

<script setup lang="ts">
import { inject, type Ref, ref, watchEffect } from "vue";
import SequenceDiagram from "@/components/sequence_diagram/SequenceDiagram.vue";
import type { Emitter } from "mitt";
import { useFeaturesStore } from "@/stores/features.ts";
import { storeToRefs } from "pinia";
import type MessageMetaDataDto from "@/types/rbel/MessageMetaDataDto.ts";

const emitter: Emitter<any> = inject("emitter") as Emitter<any>;

const diagramSteps: Ref<string[]> = ref([]);

const { rbelMetadata } = storeToRefs(useFeaturesStore());

watchEffect(() => {
  diagramSteps.value = rbelMetadata.value.map(convertToDiagramStepString);
});

function convertToDiagramStepString(metadata: MessageMetaDataDto) {
  const arrow = isResponse(metadata) ? "-->>" : "->>";
  return `${metadata.bundledServerNameSender || "no symbolic name"}${arrow}${metadata.bundledServerNameReceiver || "no symbolic name"}: #35;${metadata.sequenceNumber + 1}: ${metadata.menuInfoString}`;
}

function isResponse(metadata: MessageMetaDataDto) {
  return !metadata.request;
}

function handleClickOnSequenceNumber(sequenceNumber: number) {
  console.info("handling sequence number" + sequenceNumber);
  const metadata = rbelMetadata.value.find(
    (m) => m.sequenceNumber === sequenceNumber,
  );
  if (metadata) {
    emitter.emit("scrollToRbelLogMessage", metadata.uuid);
  }
}
</script>
