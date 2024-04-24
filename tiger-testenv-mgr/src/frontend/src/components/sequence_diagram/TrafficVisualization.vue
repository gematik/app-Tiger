<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
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
  menuInfoString: string;
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
          const rbelMetaSequenceNumber = rbelMeta.sequenceNumber + 1;
          stepRbelMetaDataList.push({
            ...rbelMeta,
            sequenceNumber: rbelMetaSequenceNumber
          });
        }
      }
    }
  }
  return stepRbelMetaDataList;
}

const parsedData = ref<RbelMetaData[]>([]);

watchEffect(() => {
  parsedData.value = parseFeatureMap(prop.featureUpdateMap);
  parsedData.value.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
  diagramSteps.value = parsedData.value.map(convertToDiagramStepString)
});

function convertToDiagramStepString(metadata: RbelMetaData) {
  const arrow = isResponse(metadata) ? "-->>" : "->>";
  return `${metadata.bundledServerNameSender || "no symbolic name"}${arrow}${metadata.bundledServerNameReceiver || "no symbolic name"}: #35;${metadata.sequenceNumber}: ${metadata.menuInfoString}`
}

function isResponse(metadata: RbelMetaData) {
  return Number.isInteger(metadata.responseCode);
}

function handleClickOnSequenceNumber(sequenceNumber: number) {
  console.info("handling sequence number" + sequenceNumber)
  const metadata = parsedData.value.find(m => m.sequenceNumber === sequenceNumber);
  if (metadata) {
    prop.ui.showRbelLogDetails(metadata?.uuid, sequenceNumber.toString(), new MouseEvent("ignore"))
  }
}


</script>
