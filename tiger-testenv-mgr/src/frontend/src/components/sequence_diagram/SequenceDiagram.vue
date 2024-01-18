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

const mermaidElement = ref<SVGElement | null>(null);
const stringToRender = computed(() => "sequenceDiagram\n" + props.diagramStepDescription.map(s => "    " + s).join("\n"));
const emit = defineEmits(['clickOnMessageWithSequenceNumber'])

function clickOnMessage(m: Node) {
  if (m.textContent) {
    const sequenceNumber = extractSequenceNumber(m.textContent)
    emit("clickOnMessageWithSequenceNumber", sequenceNumber)
  }
}

function extractSequenceNumber(messageText: string): number {
  const match = messageText.match(/^#(\d+):/);
  if (match) {
    return Number(match[1])
  }
  throw Error("failed to find a sequence number in the message text.")
}

const renderDiagram = async () => {
  if (props.diagramStepDescription.length == 0) {
    return;
  }
  try {
    const {svg}: RenderResult = await mermaid.render("mermaidDiagram-" + Math.floor(Math.random() * 1000),
        stringToRender.value, mermaidElement.value || undefined)
    if (mermaidElement.value) {
      mermaidElement.value.innerHTML = svg;
      const messages = mermaidElement.value.querySelectorAll("text.messageText");
      messages.forEach(m => {
        m.classList.add("clickableMessageText")
        m.addEventListener("click", () => clickOnMessage(m))
      })
    }
  } catch (error) {
    console.error('Error rendering Mermaid diagram:', error);
    console.debug("Error while rendering this diagram:\n", stringToRender.value);
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

<style>
.clickableMessageText {
  cursor: pointer;
  transition: fill 0.3s ease, text-decoration 0.3s ease, transform 0.1s ease;
  user-select: none;
  text-decoration: none;
  transform-origin: center;
}

.clickableMessageText:hover {
  fill: var(--gem-primary-500) !important;
  text-decoration: underline;
}

.clickableMessageText:active {
  fill: var(--gem-primary-600) !important;
  transform: scale(0.99);
}
</style>
