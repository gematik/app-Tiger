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
import { useClipboard } from "@vueuse/core";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { faCheck, faTriangleExclamation } from "@fortawesome/free-solid-svg-icons";
import { Popover, Tooltip } from "bootstrap";
import { onBeforeUnmount, onMounted, ref } from "vue";
import { parseHttpRequest, type HttpRequest } from "./httpRequestParser";
import { convertToCurl, type CurlShell } from "./httpRequestToCurl";

const props = withDefaults(
  defineProps<{ rawMessageContent: string; protocol?: "https" | "http" }>(),
  { protocol: "http" },
);
const { copy } = useClipboard({ legacy: true });
const feedbackAnchor = ref<HTMLDivElement | null>(null);
const copied = ref(false);
const errorMessage = ref("");
let tooltip: Tooltip | undefined;
let errorPopover: Popover | undefined;
let feedbackTimer: ReturnType<typeof setTimeout> | undefined;
let unmounted = false;

function dismissError() {
  errorMessage.value = "";
  errorPopover?.dispose();
  errorPopover = undefined;
}

function dismissOnOutsideClick(event: MouseEvent) {
  if (event.target instanceof Node && !feedbackAnchor.value?.contains(event.target)) {
    dismissError();
  }
}

function dismissOnEscape(event: KeyboardEvent) {
  if (event.key === "Escape") {
    dismissError();
  }
}

onMounted(() => {
  document.addEventListener("click", dismissOnOutsideClick);
  document.addEventListener("keydown", dismissOnEscape);
});

onBeforeUnmount(() => {
  unmounted = true;
  clearTimeout(feedbackTimer);
  tooltip?.dispose();
  dismissError();
  document.removeEventListener("click", dismissOnOutsideClick);
  document.removeEventListener("keydown", dismissOnEscape);
});

function showCopySuccess() {
  dismissError();
  copied.value = true;
  if (feedbackAnchor.value && !tooltip) {
    tooltip = new Tooltip(feedbackAnchor.value, {
      title: "Copied!",
      trigger: "manual",
      placement: "top",
      container: "body",
    });
  }
  tooltip?.show();
  clearTimeout(feedbackTimer);
  feedbackTimer = setTimeout(() => {
    copied.value = false;
    tooltip?.hide();
  }, 2000);
}

function showCopyError(message: string) {
  clearTimeout(feedbackTimer);
  copied.value = false;
  tooltip?.dispose();
  tooltip = undefined;
  dismissError();
  errorMessage.value = message;
  if (feedbackAnchor.value) {
    errorPopover = new Popover(feedbackAnchor.value, {
      title: "Could not copy as cURL",
      content: message + " Press Escape or click outside to dismiss.",
      trigger: "manual",
      placement: "top",
      container: "body",
      html: false,
    });
    errorPopover.show();
  }
}

async function copyAsCurl(shell: CurlShell) {
  let request: HttpRequest;
  try {
    request = parseHttpRequest(props.rawMessageContent);
  } catch (error) {
    console.error("Copy as cURL failed", error);
    showCopyError("This message could not be read as an HTTP request.");
    return;
  }

  let command: string;
  try {
    command = convertToCurl(request, shell, props.protocol);
  } catch (error) {
    console.error("Copy as cURL failed", error);
    const message =
      error instanceof Error ? error.message : "This request could not be converted to cURL.";
    showCopyError(message);
    return;
  }

  try {
    await copy(command);
  } catch (error) {
    console.error("Copy as cURL failed", error);
    if (!unmounted) {
      showCopyError("Clipboard access failed. Check your browser permissions and try again.");
    }
    return;
  }

  if (!unmounted) {
    showCopySuccess();
  }
}
</script>

<template>
  <div ref="feedbackAnchor" class="dropdown">
    <button
      class="btn modal-button modal-button-details dropdown-toggle text-dark"
      type="button"
      data-bs-toggle="dropdown"
      aria-expanded="false"
    >
      <FontAwesomeIcon v-if="copied" :icon="faCheck" class="text-success" aria-hidden="true" />
      <FontAwesomeIcon
        v-else-if="errorMessage"
        :icon="faTriangleExclamation"
        class="text-warning"
        aria-hidden="true"
      />
      cURL
    </button>
    <output class="visually-hidden">{{ copied ? "Copied!" : "" }}</output>
    <div class="visually-hidden" role="alert">
      {{ errorMessage ? "Could not copy as cURL. " + errorMessage : "" }}
    </div>
    <ul class="dropdown-menu">
      <li>
        <a @click.prevent="copyAsCurl('bash')" class="dropdown-item" href="#"
          >Copy as cURL (bash)</a
        >
      </li>
      <li>
        <a @click.prevent="copyAsCurl('cmd')" class="dropdown-item" href="#">Copy as cURL (cmd) </a>
      </li>
    </ul>
  </div>
</template>
