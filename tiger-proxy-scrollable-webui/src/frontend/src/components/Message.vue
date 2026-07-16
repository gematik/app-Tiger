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
import type { Message, MessageUiState } from "@/api/MessageQueue.ts";
import { messageQueueSymbol } from "@/api/MessageQueue.ts";
import { h, inject, nextTick, onBeforeUnmount, type Ref, ref, render, watch } from "vue";
import InspectButton from "@/components/InspectButton.vue";
import { rbelQueryModalSymbol } from "../RbelQueryModal.ts";
import { settingsSymbol } from "../Settings.ts";
import { rawContentModalSymbol } from "../RawContentModal.ts";
import hljs from "highlight.js/lib/core";
import "highlight.js/styles/stackoverflow-dark.css";

const props = defineProps<{
  message: Message;
  onToggleDetailsOrHeader: () => void;
}>();

const settings = inject(settingsSymbol)!;
const rbelQuery = inject(rbelQueryModalSymbol)!;
const rawContentModal = inject(rawContentModalSymbol)!;

const messageElement: Ref<HTMLElement | null> = ref(null);

const toggleMessageDetails = ref<((isHidden: boolean) => void) | null>(null);
const toggleMessageHeaders = ref<((isHidden: boolean) => void) | null>(null);
const toggleMessageBody = ref<((isHidden: boolean) => void) | null>(null);

const messageQueue = inject(messageQueueSymbol)!;

const delegatedHandlerAttached = ref(false);
const attachedElement: Ref<HTMLElement | null> = ref(null);

// Track which messages have been initialized using a WeakMap on the uiState object
// This persists across component unmount/remount cycles (unlike component state refs)
function isMessageInitialized(uuid: string): boolean {
  const state = messageQueue.internal.getUiState(uuid);
  return (state as any)._initialized === true;
}

function markMessageInitialized(uuid: string) {
  const state = messageQueue.internal.getUiState(uuid);
  (state as any)._initialized = true;
}

function handleSectionToggle(
  event: MouseEvent,
  findAncestorWithClass: (el: Element | null, cls: string) => Element | null,
  sectionToggle: Element,
) {
  event.stopPropagation();
  event.preventDefault();
  const section = findAncestorWithClass(sectionToggle, "msg-section");
  if (!section) return;
  const sectionContent = section.querySelector(".msg-section-content");
  if (!sectionContent) return;
  const isHidden = sectionContent.classList.toggle("d-none");
  elementToggleIcon(sectionToggle, !isHidden);
  const state = messageQueue.internal.getUiState(props.message.uuid);
  const sections = state.sections ?? (state.sections = {});
  let sid =
    sectionToggle.getAttribute("data-section-id") ?? sectionContent.getAttribute("data-section-id");
  if (!sid) {
    sid = `sec-${Math.random().toString(36).slice(2, 9)}`;
    sectionToggle.setAttribute("data-section-id", sid);
    sectionContent.setAttribute("data-section-id", sid);
  }
  sections[sid] = !isHidden;
  props.onToggleDetailsOrHeader();
}

function handleHeaderToggle(event: MouseEvent, headerToggle: Element) {
  event.stopPropagation();
  event.preventDefault();
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");
  const headerContent = messageContent?.querySelector(".msg-header-content");
  if (!headerContent) return;
  const isHidden = headerContent.classList.toggle("d-none");
  elementToggleIcon(headerToggle, !isHidden);
  const state = messageQueue.internal.getUiState(props.message.uuid);
  state.headers = !isHidden;
  props.onToggleDetailsOrHeader();
}

function handleBodyToggle(event: MouseEvent, bodyToggle: Element) {
  event.stopPropagation();
  event.preventDefault();
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");
  const bodyContent = messageContent?.querySelector(".msg-body-content");
  if (!bodyContent) return;
  const isHidden = bodyContent.classList.toggle("d-none");
  elementToggleIcon(bodyToggle, !isHidden);
  const state = messageQueue.internal.getUiState(props.message.uuid);
  state.body = !isHidden;
  props.onToggleDetailsOrHeader();
}

function handleMessageToggle(msgToggle: Element) {
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");
  if (!messageContent) {
    return;
  }
  const isHidden = messageContent.classList.toggle("d-none");
  elementToggleIcon(msgToggle, !isHidden);
  const state = messageQueue.internal.getUiState(props.message.uuid);
  state.details = !isHidden;
  props.onToggleDetailsOrHeader();
}

function delegatedClickHandler(event: MouseEvent) {
  if (!messageElement.value) {
    return;
  }
  const target = event.target as Element;

  // helper to find ancestor matching class
  function findAncestorWithClass(el: Element | null, cls: string): Element | null {
    while (el && el !== messageElement.value) {
      if (el.classList && el.classList.contains(cls)) return el;
      el = el.parentElement;
    }
    return null;
  }

  // Check for more specific classes FIRST before generic toggle-icon
  // section toggles (must be checked before generic toggle-icon!)
  const sectionToggle = findAncestorWithClass(target, "msg-section-toggle");
  if (sectionToggle) {
    handleSectionToggle(event, findAncestorWithClass, sectionToggle);
    return;
  }

  const headerToggle = findAncestorWithClass(target, "header-toggle");
  if (headerToggle) {
    handleHeaderToggle(event, headerToggle);
    return;
  }

  const bodyToggle = findAncestorWithClass(target, "body-toggle");
  if (bodyToggle) {
    handleBodyToggle(event, bodyToggle);
    return;
  }

  const msgToggle =
    findAncestorWithClass(target, "msg-toggle") ||
    findAncestorWithClass(target, "toggle-icon") ||
    null;
  if (msgToggle) {
    // toggle full details
    handleMessageToggle(msgToggle);
  }
}

onBeforeUnmount(() => {
  if (messageElement.value && delegatedHandlerAttached.value) {
    messageElement.value.removeEventListener("click", delegatedClickHandler);
    delegatedHandlerAttached.value = false;
  }
});

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

watch(
  () => props.message.uuid,
  (newUuid) => {
    // Reset initialization flag for the new message UUID
    const state = messageQueue.internal.getUiState(newUuid);
    (state as any)._initialized = false;
  },
);

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

function elementToggleIcon(el: Element, isToggled: boolean) {
  el.classList.remove("fa-toggle-on", "fa-toggle-off");
  if (isToggled) {
    el.classList.add("fa-toggle-on");
  } else {
    el.classList.add("fa-toggle-off");
  }
}

function reattachHandler() {
  if (
    messageElement.value &&
    (!delegatedHandlerAttached.value || attachedElement.value !== messageElement.value)
  ) {
    messageElement.value.addEventListener("click", delegatedClickHandler);
    delegatedHandlerAttached.value = true;
    attachedElement.value = messageElement.value;
  }
}

function restoreDetailsVisibility(
  messageContent: any,
  state: MessageUiState,
  messageContentToggle: any,
) {
  if (messageContent && state.details !== undefined) {
    if (state.details) {
      messageContent.classList.remove("d-none");
    } else {
      messageContent.classList.add("d-none");
    }
    if (messageContentToggle) {
      elementToggleIcon(messageContentToggle, state.details);
    }
  }
}

function restoreHeadersVisibility(messageContent: any, state: MessageUiState) {
  const messageRequestHeader = messageContent?.querySelector(".msg-header-content");
  const messageRequestHeaderToggle = messageContent?.querySelector(".header-toggle");
  if (messageRequestHeader && state.headers !== undefined) {
    if (state.headers) {
      messageRequestHeader.classList.remove("d-none");
    } else {
      messageRequestHeader.classList.add("d-none");
    }
    if (messageRequestHeaderToggle) {
      elementToggleIcon(messageRequestHeaderToggle, state.headers);
    }
  }
}

function restoreBodyVisibility(messageContent: any, state: MessageUiState) {
  const messageRequestBody = messageContent?.querySelector(".msg-body-content");
  const messageRequestBodyToggle = messageContent?.querySelector(".body-toggle");
  if (messageRequestBody && state.body !== undefined) {
    if (state.body) {
      messageRequestBody.classList.remove("d-none");
    } else {
      messageRequestBody.classList.add("d-none");
    }
    if (messageRequestBodyToggle) {
      elementToggleIcon(messageRequestBodyToggle, state.body);
    }
  }
}

function computeSectionId(
  sectionToggle: Element,
  sectionContent: Element,
  sections: Record<string, boolean>,
  i: number,
) {
  let sid =
    sectionToggle.getAttribute("data-section-id") ?? sectionContent.getAttribute("data-section-id");

  if (!sid && Object.keys(sections).length > 0) {
    const storedIds = Object.keys(sections);
    if (i < storedIds.length) {
      sid = storedIds[i];
    }
  }

  if (!sid) {
    sid = `sec-${Math.random().toString(36).slice(2, 9)}`;
  }
  return sid;
}

function restoreSectionVisibilityState(messageSections: any, i: number, state: MessageUiState) {
  const messageSection = messageSections[i] as Element;
  const sectionToggle = messageSection.querySelector(".msg-section-toggle");
  const sectionContent = messageSection.querySelector(".msg-section-content");
  if (sectionToggle && sectionContent) {
    const sections = state.sections ?? (state.sections = {});
    const sid = computeSectionId(sectionToggle, sectionContent, sections, i);
    sectionToggle.setAttribute("data-section-id", sid);
    sectionContent.setAttribute("data-section-id", sid);

    const storedVisible = sections[sid];
    if (storedVisible !== undefined) {
      if (storedVisible) {
        sectionContent.classList.remove("d-none");
      } else {
        sectionContent.classList.add("d-none");
      }
      elementToggleIcon(sectionToggle, storedVisible);
    }
  }
}

function restoreSectionsVisibilityState(state: MessageUiState) {
  const messageSections = messageElement.value?.querySelectorAll(".msg-section");
  if (messageSections) {
    for (let i = 0; i < messageSections.length; i++) {
      restoreSectionVisibilityState(messageSections, i, state);
    }
  }
}

function restoreMessageState(msgUuid: any) {
  const state = messageQueue.internal.getUiState(msgUuid);
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");
  const messageHeader = messageElement.value?.querySelector(".card-header");
  const messageContentToggle = messageHeader?.querySelector(".toggle-icon.msg-toggle");

  restoreDetailsVisibility(messageContent, state, messageContentToggle);
  restoreHeadersVisibility(messageContent, state);
  restoreBodyVisibility(messageContent, state);
  restoreSectionsVisibilityState(state);
}

function restoreSectionVisibility(
  state: MessageUiState,
  sectionToggle: Element,
  sectionContent: Element,
  i: number,
) {
  const sections = state.sections ?? (state.sections = {});
  let sid =
    sectionToggle.getAttribute("data-section-id") ?? sectionContent.getAttribute("data-section-id");

  if (!sid && Object.keys(sections).length > 0) {
    const storedIds = Object.keys(sections);
    if (i < storedIds.length) {
      sid = storedIds[i];
    }
  }

  if (!sid) {
    sid = `sec-${Math.random().toString(36).slice(2, 9)}`;
  }
  sectionToggle.setAttribute("data-section-id", sid);
  sectionContent.setAttribute("data-section-id", sid);

  const storedVisible = sections[sid];
  const currentlyHidden = sectionContent.classList.contains("d-none");
  if (storedVisible === undefined) {
    sections[sid] = !currentlyHidden;
  } else if (storedVisible !== !currentlyHidden) {
    if (storedVisible) {
      sectionContent.classList.remove("d-none");
      elementToggleIcon(sectionToggle, true);
    } else {
      sectionContent.classList.add("d-none");
      elementToggleIcon(sectionToggle, false);
    }
  }
}

function restoreSectionsVisibility(state: MessageUiState) {
  const messageSections = messageElement.value?.querySelectorAll(".msg-section");
  if (messageSections) {
    for (let i = 0; i < messageSections.length; i++) {
      const messageSection = messageSections[i] as Element;
      const sectionToggle = messageSection.querySelector(".msg-section-toggle");
      const sectionContent = messageSection.querySelector(".msg-section-content");
      if (sectionToggle && sectionContent) {
        restoreSectionVisibility(state, sectionToggle, sectionContent, i);
      }
    }
  }
}

function setInitialMessageState() {
  const state = messageQueue.internal.getUiState(props.message.uuid);
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");

  // Sync details state with DOM ONLY if not yet explicitly set
  if (messageContent && state.details === undefined) {
    const detailsCurrentlyHidden = messageContent.classList.contains("d-none");
    state.details = !detailsCurrentlyHidden;
  }

  const messageRequestHeader = messageContent?.querySelector(".msg-header-content");
  if (messageRequestHeader && state.headers === undefined) {
    const headersCurrentlyHidden = messageRequestHeader.classList.contains("d-none");
    state.headers = !headersCurrentlyHidden;
  }

  const messageRequestBody = messageContent?.querySelector(".msg-body-content");
  if (messageRequestBody && state.body === undefined) {
    const bodyCurrentlyHidden = messageRequestBody.classList.contains("d-none");
    state.body = !bodyCurrentlyHidden;
  }

  // Restore section visibility (sections might have been toggled while not visible)
  restoreSectionsVisibility(state);
}

function detachListenerForChangedElement() {
  if (attachedElement.value && attachedElement.value !== messageElement.value) {
    try {
      attachedElement.value.removeEventListener("click", delegatedClickHandler);
    } catch (e) {
      console.error(e);
    }
    attachedElement.value = null;
  }
}

function setupRawMessageButton() {
  // The raw content of the message (e.g. the http message)
  const rawContentModalButtons = messageElement.value?.querySelectorAll(
    "div [data-bs-target^='#dialog']",
  );
  const rawContentModalContents = messageElement.value?.querySelectorAll("div [role='dialog']");
  if (
    rawContentModalButtons &&
    rawContentModalContents &&
    rawContentModalButtons.length === rawContentModalContents.length
  ) {
    for (let i = 0; i < rawContentModalButtons.length; i++) {
      const rawContentModalButton = rawContentModalButtons[i];
      const rawContentModalContent = rawContentModalContents[i];
      const rawContentText = rawContentModalContent.querySelector("pre")?.innerHTML ?? "";

      rawContentModalButton.setAttribute("data-bs-target", "#rawContentModal");
      rawContentModalButton.addEventListener("click", () => {
        rawContentModal.show(props.message, rawContentText);
      });

      rawContentModalContent.remove();
    }
  }
}

function setupFullRenderButton() {
  const fullRenderButton = messageElement.value?.querySelector(".full-message-button");
  if (fullRenderButton) {
    fullRenderButton.addEventListener("click", (event) => {
      event.preventDefault();
      const url = `/message/${props.message.uuid}`;
      const newWindow = window.open(url);
      if (!newWindow) {
        alert("Popup blocked. Please allow popups for this site.");
      }
    });
  }
}

function setupCodeHighlighting() {
  // Code highlighting
  messageElement.value?.querySelectorAll("pre.json").forEach((el) => {
    if (el.getAttribute("data-hljs-highlighted") !== "true") {
      hljs.highlightElement(el as HTMLElement);
      el.setAttribute("data-hljs-highlighted", "true");
    }
  });
}

async function setupInteractiveMessageContent(msgUuid: any) {
  const messageHeader = messageElement.value?.querySelector(".card-header");

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

  setupRawMessageButton();

  // During initial render, sync state map with the actual DOM state
  // (the HTML from server has the correct initial state, so read from it!)
  // IMPORTANT: Only set state if not already explicitly set by user (to preserve toggles across re-renders)
  if (messageElement.value) {
    setInitialMessageState();
  }

  // Setup callbacks for global settings (hide all headers/details)
  // These are used by the global hide toggles in the UI
  const messageContent = messageElement.value?.querySelector(".card-content.msg-content");
  const messageContentToggle = messageHeader?.querySelector(".toggle-icon.msg-toggle");
  const state = messageQueue.internal.getUiState(props.message.uuid);

  if (messageContent && messageContentToggle) {
    toggleMessageContent(state, messageContentToggle, messageContent);
  }

  const messageRequestHeader = messageContent?.querySelector(".msg-header-content");
  const messageRequestHeaderToggle = messageContent?.querySelector(".header-toggle");
  if (messageRequestHeader && messageRequestHeaderToggle) {
    toggleMessageHeader(state, messageRequestHeaderToggle, messageRequestHeader);
  }

  const messageRequestBody = messageContent?.querySelector(".msg-body-content");
  const messageRequestBodyToggle = messageContent?.querySelector(".body-toggle");
  if (messageRequestBody && messageRequestBodyToggle) {
    toggleMessageRequestBody(state, messageRequestBodyToggle, messageRequestBody);
  }

  // Attach delegated click handler once per messageElement
  if (messageElement.value) {
    attachClickHandler();
  }

  setupCodeHighlighting();

  setupFullRenderButton();

  // Mark initialization complete to prevent re-running this watch for this message UUID
  markMessageInitialized(msgUuid);
}

function attachClickHandler() {
  if (!delegatedHandlerAttached.value || attachedElement.value !== messageElement.value) {
    // ensure any previous handler removed
    if (attachedElement.value && attachedElement.value !== messageElement.value) {
      try {
        attachedElement.value.removeEventListener("click", delegatedClickHandler);
      } catch (e) {
        console.error(e);
      }
    }
    messageElement.value?.addEventListener("click", delegatedClickHandler);
    delegatedHandlerAttached.value = true;
    attachedElement.value = messageElement.value;
  }
}

async function setHtmlContentForRecycledElement(htmlContent: string, msgUuid: any) {
  if (messageElement.value) {
    messageElement.value.innerHTML = htmlContent;
    await nextTick();
  }

  // Reattach handler to the new element if it changed
  reattachHandler();

  // CRITICAL: For recycled elements, restore from state map, NOT from fresh HTML
  // The fresh HTML has default state, but we need to apply the user's toggles
  if (messageElement.value) {
    restoreMessageState(msgUuid);
  }

  // Rebuild interactive DOM that was lost when the virtual scroller recycled this row.
  await setupInteractiveMessageContent(msgUuid);
}

function toggleMessageContent(
  state: MessageUiState,
  messageContentToggle: any,
  messageContent: Element,
) {
  toggleMessageDetails.value = (isHidden: boolean) => {
    state.details = !isHidden;
    toggle(messageContentToggle, messageContent, isHidden);
    props.onToggleDetailsOrHeader();
  };
}

function toggleMessageRequestBody(
  state: MessageUiState,
  messageRequestBodyToggle: any,
  messageRequestBody: any,
) {
  toggleMessageBody.value = (isHidden: boolean) => {
    state.body = !isHidden;
    toggle(messageRequestBodyToggle, messageRequestBody, isHidden);
    props.onToggleDetailsOrHeader();
  };
}

function toggleMessageHeader(
  state: MessageUiState,
  messageRequestHeaderToggle: any,
  messageRequestHeader: any,
) {
  toggleMessageHeaders.value = (isHidden: boolean) => {
    state.headers = !isHidden;
    toggle(messageRequestHeaderToggle, messageRequestHeader, isHidden);
    props.onToggleDetailsOrHeader();
  };
}

async function handleUninitializedElement(msgUuid: any, htmlContent: string) {
  if (messageElement.value) {
    messageElement.value.innerHTML = htmlContent;
  }
  // Wait for the inner html to be rendered
  await nextTick();

  await setupInteractiveMessageContent(msgUuid);
}

watch(messageElement, async () => {
  if (messageElement.value && props.message.type === "loaded") {
    const msgUuid = (props.message as any).uuid;

    // If the underlying element instance changed (due to virtualization),
    // detach listeners/observers from the previously attached element
    detachListenerForChangedElement();

    // If we already initialized THIS MESSAGE UUID, skip re-initialization
    // (even if a new DOM element instance was assigned due to virtual scroller recycling)
    // BUT: we still need to restore the visual state (d-none classes) and reattach the handler
    if (isMessageInitialized(msgUuid)) {
      await setHtmlContentForRecycledElement(props.message.htmlContent, msgUuid);
      return;
    }
    await handleUninitializedElement(msgUuid, props.message.htmlContent);
  }
});
</script>

<template>
  <div class="rbel-message pb-3" id="test-rbel-section">
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
