<!--

    Copyright 2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<script setup lang="ts">
import type { Message } from "@/api/MessageQueue.ts";
import { h, inject, nextTick, ref, type Ref, render, watch } from "vue";
import InspectButton from "@/components/InspectButton.vue";
import { rbelQueryModalSymbol } from "../RbelQueryModal.ts";
import { settingsSymbol } from "../Settings.ts";
import { rawContentModalSymbol } from "../RawContentModal.ts";
import hljs from "highlight.js/lib/core";
import "highlight.js/styles/stackoverflow-dark.css";

const props = defineProps<{ message: Message; onToggleDetailsOrHeader: () => void }>();

const settings = inject(settingsSymbol)!;
const rbelQuery = inject(rbelQueryModalSymbol)!;
const rawContentModal = inject(rawContentModalSymbol)!;

const messageElement: Ref<HTMLElement | null> = ref(null);

const rawContent = ref("");
const toggleMessageDetails = ref<((isHidden: boolean) => void) | null>(null);
const toggleMessageHeaders = ref<((isHidden: boolean) => void) | null>(null);
const toggleMessageBody = ref<((isHidden: boolean) => void) | null>(null);

watch(settings.hideMessageDetails, () => {
  toggleMessageDetails.value?.(settings.hideMessageDetails.value);
});

watch(settings.hideMessageHeaders, () => {
  toggleMessageHeaders.value?.(settings.hideMessageHeaders.value);
});

watch([toggleMessageDetails, toggleMessageHeaders], () => {
  nextTick(async () => {
    // this is a hack to properly trigger the resize of the messages for the dynamic list
    await new Promise((resolve) => setTimeout(resolve, 1));
    toggleMessageDetails.value?.(settings.hideMessageDetails.value);
    toggleMessageHeaders.value?.(settings.hideMessageHeaders.value);
  });
});

function toggle(iconEl: Element, contentEl: Element, isHidden: boolean) {
  const cl = contentEl.classList;
  if (isHidden) {
    cl.add("d-none");
    elementToggleIcon(iconEl, false);
  } else {
    cl.remove("d-none");
    elementToggleIcon(iconEl, true);
  }
}

function elementHasToggledOnIcon(el: Element): boolean {
  return el.classList.contains("fa-toggle-on");
}

function elementToggleIcon(el: Element, isToggled: boolean) {
  el.classList.remove("fa-toggle-on", "fa-toggle-off");
  if (isToggled) {
    el.classList.add("fa-toggle-on");
  } else {
    el.classList.add("fa-toggle-off");
  }
}

watch(messageElement, async () => {
  if (messageElement.value && props.message.type === "loaded") {
    messageElement.value.innerHTML = props.message.htmlContent;
    // Wait for the inner html to be rendered
    await nextTick();

    //
    // Warning: If the underlying message structure changes, this might fail here; so always keep in sync!

    const messageHeader = messageElement.value.querySelector(".card-header");

    if (__IS_ONLINE_MODE__) {
      const messageHeaderEntries = messageHeader?.querySelector("span");
      if (messageHeaderEntries) {
        const vnode = h(InspectButton, {
          onShowModal: () => {
            rbelQuery.show(props.message);
          },
        });
        render(vnode, messageHeaderEntries);
        await nextTick();
        props.onToggleDetailsOrHeader();
      }
    }

    // The raw content of the message (e.g. the http message)
    const rawContentModalButtons = messageElement.value.querySelectorAll(
      "div [data-bs-target^='#dialog']",
    );
    const rawContentModalContents = messageElement.value.querySelectorAll("div [role='dialog']");
    if (
      rawContentModalButtons &&
      rawContentModalContents &&
      rawContentModalButtons.length === rawContentModalContents.length
    ) {
      for (let i = 0; i < rawContentModalButtons.length; i++) {
        const rawContentModalButton = rawContentModalButtons[i];
        const rawContentModalContent = rawContentModalContents[i];

        rawContentModalButton.setAttribute("data-bs-target", "#rawContentModal");
        rawContentModalButton.addEventListener("click", () => {
          rawContentModal.show(props.message, rawContent.value);
        });

        rawContent.value += rawContentModalContent.querySelector("pre")?.innerHTML ?? "";
        rawContentModalContent.remove();
      }
    }

    // Message details (e.g. including the body)
    const messageContent = messageElement.value.querySelector(".card-content.msg-content");
    const messageContentToggle = messageHeader?.querySelector(".toggle-icon.msg-toggle");
    if (messageContent && messageContentToggle) {
      toggleMessageDetails.value = (isHidden: boolean) => {
        toggle(messageContentToggle, messageContent, isHidden);
        props.onToggleDetailsOrHeader();
      };
      messageContentToggle.addEventListener("click", () => {
        const toggledOn = elementHasToggledOnIcon(messageContentToggle);
        toggleMessageDetails.value?.(toggledOn);
      });
    }

    // Message header
    const messageRequestHeader = messageContent?.querySelector(".msg-header-content");
    const messageRequestHeaderToggle = messageContent?.querySelector(".header-toggle");
    if (messageRequestHeader && messageRequestHeaderToggle) {
      toggleMessageHeaders.value = (isHidden: boolean) => {
        toggle(messageRequestHeaderToggle, messageRequestHeader, isHidden);
        props.onToggleDetailsOrHeader();
      };
      messageRequestHeaderToggle.addEventListener("click", () => {
        const toggledOn = elementHasToggledOnIcon(messageRequestHeaderToggle);
        toggleMessageHeaders.value?.(toggledOn);
      });
    }

    // Message body
    const messageRequestBody = messageContent?.querySelector(".msg-body-content");
    const messageRequestBodyToggle = messageContent?.querySelector(".body-toggle");
    if (messageRequestBody && messageRequestBodyToggle) {
      toggleMessageBody.value = (isHidden: boolean) => {
        toggle(messageRequestBodyToggle, messageRequestBody, isHidden);
        props.onToggleDetailsOrHeader();
      };
      messageRequestBodyToggle.addEventListener("click", () => {
        const toggledOn = elementHasToggledOnIcon(messageRequestBodyToggle);
        toggleMessageBody.value?.(toggledOn);
      });
    }

    // Code highlighting
    messageElement.value.querySelectorAll("pre.json").forEach((el) => {
      if (el.getAttribute("data-hljs-highlighted") !== "true") {
        hljs.highlightElement(el as HTMLElement);
        el.setAttribute("data-hljs-highlighted", "true");
      }
    });

    // End
    //
  }
});
</script>

<template>
  <div class="rbel-message pb-3">
    <div v-if="props.message.type === 'loaded'" ref="messageElement" />
    <div v-else class="loading">
      <div>Loading...</div>
    </div>
  </div>
</template>

<style lang="scss">
@use "../scss/rbel-messages";

.loading {
  display: flex;
  align-content: center;
  min-height: 500px;
}

// fixes overflowing the content if a table is too wide
div:has(> .table) {
  max-width: 100%;
  overflow-x: auto;
  display: block;

  table {
    td {
      pre {
        overflow: hidden;
      }
    }
  }
}

.container {
  max-width: 1400px !important;
}
</style>
