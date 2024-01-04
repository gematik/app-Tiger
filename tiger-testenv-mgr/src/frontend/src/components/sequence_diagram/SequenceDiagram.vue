<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">
import mermaid, {RenderResult} from "mermaid";
import {computed, onMounted, ref, watch} from "vue";

mermaid.initialize({startOnLoad: false})

const props = defineProps<{
  diagramStepDescription: string[]
}>()

const mermaidElement = ref<HTMLElement | null>(null);
const stringToRender = computed(() => "sequenceDiagram\n" + props.diagramStepDescription.map(s => "    " + s).join("\n"));
const emit = defineEmits(['clickOnMessageWithSequenceNumber'])

function clickOnMessage(m: Node) {
  const match = m.textContent?.match(/#(\d+):/);
  if (match) {
    const sequenceNumber: number = Number(match[1]);
    emit("clickOnMessageWithSequenceNumber", sequenceNumber)
  }
}

const renderDiagram = async () => {
  if (props.diagramStepDescription.length == 0) {
    return;
  }
  try {
    console.log("RENDERING")
    console.log(stringToRender.value)
    const {svg}: RenderResult = await mermaid.render("mermaidDiagram-" + Math.floor(Math.random() * 1000), stringToRender.value, mermaidElement.value || undefined)
    if (mermaidElement.value) {
      mermaidElement.value.innerHTML = svg;
      const messages = mermaidElement.value.querySelectorAll("text.messageText");
      messages.forEach(m => m.addEventListener("click", () => clickOnMessage(m)))
    }
    console.log("=======")
  } catch (error) {
    console.error('Error rendering Mermaid diagram:', error);
  }
}

onMounted(() => {
  renderDiagram();
});

watch(() => stringToRender.value, () => {
  renderDiagram();
});
</script>


<template>
<pre ref="mermaidElement">
</pre>
</template>