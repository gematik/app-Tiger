<!--
  - Copyright (c) 2024 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->
<template>
  <div class="tab-pane execution-pane-tabs" id="visualization_pane" role="tabpanel">
    <sequence-diagram :diagram-step-description="diagramSteps"
                      @click-on-message-with-sequence-number="handleClickOnSequenceNumber"></sequence-diagram>
  </div>
</template>

<script setup lang="ts">
import {Ref, ref, watchEffect} from 'vue';
import FeatureUpdate from "@/types/testsuite/FeatureUpdate";
import Ui from "@/types/ui/Ui";
import SequenceDiagram from "@/components/sequence_diagram/SequenceDiagram.vue";


const prop = defineProps<{
  featureUpdateMap: Map<string, FeatureUpdate>;
  ui: Ui;
}>();

const diagramSteps: Ref<string[]> = ref([]);

interface RbelMetaData {
  uuid: string;
  path: string;
  method: string;
  responseCode: number;
  recipient: string;
  sequenceNumber: number;
  bundledServerNameSender: string;
  bundledServerNameReceiver: string;
  timestamp: Date | string;
}

function parseFeatureMap(featureUpdateMap: Map<string, FeatureUpdate>): RbelMetaData[] {
  const stepRbelMetaDataList: RbelMetaData[] = [];

  for (const [, feature] of featureUpdateMap) {
    for (const [, scenario] of feature.scenarios) {
      for (const [, step] of scenario.steps) {
        for (const rbelMeta of step.rbelMetaData) {
          let rbelMetaSequenceNumber = rbelMeta.sequenceNumber + 1;
          stepRbelMetaDataList.push({
            ...rbelMeta,
            sequenceNumber: rbelMetaSequenceNumber,
          });
        }
      }
    }
  }
  return stepRbelMetaDataList;
}

let parsedData = ref<RbelMetaData[]>([]);

watchEffect(() => {
  parsedData.value = parseFeatureMap(prop.featureUpdateMap);
  parsedData.value.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  diagramSteps.value = parsedData.value.map(convertToDiagramStepString)
});

function convertToDiagramStepString(metadata: RbelMetaData) {
  if (isResponse(metadata)) {
    return `${metadata.bundledServerNameSender || "no symbolic name"}-->>${metadata.bundledServerNameReceiver || "no symbolic name"}: #35;${metadata.sequenceNumber}: ${metadata.responseCode}`
  } else {
    return `${metadata.bundledServerNameSender || "no symbolic name"}->>${metadata.bundledServerNameReceiver || "no symbolic name"}: #35;${metadata.sequenceNumber}: ${metadata.method} ${metadata.path}`
  }
}

function isResponse(metadata: RbelMetaData) {
  return Number.isInteger(metadata.responseCode);
}

function handleClickOnSequenceNumber(sequenceNumber: number) {
  console.info("handling sequence number" + sequenceNumber)
  const metadata = parsedData.value.find(m => m.sequenceNumber === sequenceNumber);
  if (metadata) {
    //TODO: it does not work yet
    prop.ui.showRbelLogDetails(metadata?.uuid, sequenceNumber.toString(), new MouseEvent("ignore"))
  }
}


</script>
