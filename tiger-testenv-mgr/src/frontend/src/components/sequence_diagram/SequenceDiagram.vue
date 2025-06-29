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
<script setup lang="ts">
import mermaid, { RenderResult } from "mermaid";
import { computed, onMounted, ref, watch } from "vue";

mermaid.initialize({ startOnLoad: false });

const props = defineProps<{
  diagramStepDescription: string[];
}>();

const headerElement = ref<SVGElement | null>(null);
const mermaidElement = ref<SVGElement | null>(null);
const stringToRender = computed(
  () =>
    "sequenceDiagram\n" +
    props.diagramStepDescription.map((s) => "    " + s).join("\n"),
);
const emit = defineEmits(["clickOnMessageWithSequenceNumber"]);

function clickOnMessage(m: Node) {
  if (m.textContent) {
    const sequenceNumber = extractSequenceNumber(m.textContent);
    emit("clickOnMessageWithSequenceNumber", sequenceNumber);
  }
}

function extractSequenceNumber(messageText: string): number {
  const match = messageText.match(/^#(\d+):/);
  if (match) {
    return Number(match[1]) - 1;
  }
  throw Error("failed to find a sequence number in the message text.");
}

const renderDiagram = async () => {
  if (props.diagramStepDescription.length == 0) {
    return;
  }
  try {
    const { svg }: RenderResult = await mermaid.render(
      "mermaidDiagram-" + Math.floor(Math.random() * 1000),
      stringToRender.value,
      mermaidElement.value || undefined,
    );
    if (mermaidElement.value && headerElement.value) {
      headerElement.value.innerHTML = svg;
      headerElement.value?.querySelectorAll("line").forEach((e) => e.remove());
      headerElement.value
        ?.querySelectorAll("text.messageText")
        .forEach((e) => e.remove());
      headerElement.value
        ?.querySelectorAll("rect.actor-bottom")
        .forEach((e) => e.parentElement?.remove());
      mermaidElement.value.innerHTML = svg;
      mermaidElement.value
        ?.querySelectorAll("rect.actor-top")
        .forEach((e) => e.parentElement?.remove());

      const messages =
        mermaidElement.value.querySelectorAll("text.messageText");
      messages.forEach((m) => {
        m.classList.add("clickableMessageText");
        m.addEventListener("mousedown", () => clickOnMessage(m));
      });
    }
  } catch (error) {
    console.error("Error rendering Mermaid diagram:", error);
    console.debug(
      "Error while rendering this diagram:\n",
      stringToRender.value,
    );
  }
};

onMounted(() => {
  renderDiagram();
});

watch(
  () => stringToRender.value,
  () => {
    renderDiagram();
  },
);
</script>

<template>
  <pre ref="headerElement" style="position: fixed; width: 100%"></pre>
  <pre
    ref="mermaidElement"
    style="position: relative; z-index: 1000; padding-top: 60px"
  ></pre>
</template>

<style>
.clickableMessageText {
  cursor: pointer;
  transition:
    fill 0.3s ease,
    text-decoration 0.3s ease,
    transform 0.1s ease;
  user-select: none;
  text-decoration: none;
  transform-origin: center;
  pointer-events: all;
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
