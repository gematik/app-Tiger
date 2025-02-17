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
import hljs from "highlight.js";
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

    const messageContent = messageHeader?.nextElementSibling?.querySelector(
      "div.tile.is-parent.is-vertical",
    );
    const messageContentToggle = messageHeader?.querySelector(".toggle-icon");
    if (messageContent && messageContentToggle) {
      toggleMessageDetails.value = (isHidden: boolean) => {
        toggle(messageContentToggle, messageContent, isHidden);
        props.onToggleDetailsOrHeader();
      };
      messageContentToggle.addEventListener("click", () => {
        const toggledOn = elementHasToggledOnIcon(messageContentToggle);
        toggleMessageDetails.value?.(toggledOn);
      });
      toggleMessageDetails.value?.(settings.hideMessageDetails.value);
    }

    const messageRequestHeader = messageContent?.firstElementChild?.querySelector(".card-content");
    const messageRequestHeaderToggle =
      messageContent?.firstElementChild?.querySelector(".toggle-icon");
    if (messageRequestHeader && messageRequestHeaderToggle) {
      toggleMessageHeaders.value = (isHidden: boolean) => {
        toggle(messageRequestHeaderToggle, messageRequestHeader, isHidden);
        props.onToggleDetailsOrHeader();
      };
      messageRequestHeaderToggle.addEventListener("click", () => {
        const toggledOn = elementHasToggledOnIcon(messageRequestHeaderToggle);
        toggleMessageHeaders.value?.(toggledOn);
      });
      toggleMessageHeaders.value?.(settings.hideMessageHeaders.value);
    }

    if (messageContent?.firstElementChild !== messageContent?.lastElementChild) {
      const messageRequestBody = messageContent?.lastElementChild?.querySelector(".card-content");
      const messageRequestBodyToggle =
        messageContent?.lastElementChild?.querySelector(".toggle-icon");
      if (messageRequestBody && messageRequestBodyToggle) {
        toggleMessageBody.value = (isHidden: boolean) => {
          toggle(messageRequestBodyToggle, messageRequestBody, isHidden);
          props.onToggleDetailsOrHeader();
        };
        messageRequestBodyToggle.addEventListener("click", () => {
          const toggledOn = elementHasToggledOnIcon(messageRequestBodyToggle);
          toggleMessageBody.value?.(toggledOn);
        });
        toggleMessageBody.value?.(settings.hideMessageHeaders.value);
      }
    }

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
    <div v-if="props.message.type === 'loading'" class="loading">
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
</style>
