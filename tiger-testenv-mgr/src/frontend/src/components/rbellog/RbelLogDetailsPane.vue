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
<template>
  <div
    ref="detailsResizer"
    id="rbellog_resize"
    :style="resizerStyle"
    :class="['position-fixed']"
    @click.stop="onClick"
  >
    <div
      id="test-webui-slider"
      role="button"
      :title="tooltipExpandMinimize"
      :class="[
        'fs-1',
        'fa-solid',
        expanded ? 'fa-angles-right' : 'fa-angles-left',
        'resizer-right',
      ]"
      @click.stop="onClick"
      tabindex="0"
      aria-label="Toggle Tiger Proxy Log"
    ></div>
  </div>
  <div
    id="rbellog_details_pane"
    :style="paneStyle"
    ref="detailsPane"
    class="position-fixed pl-3 pt-3"
    v-show="expanded"
  >
    <h2>
      <img
        id="test-rbel-logo"
        alt="RBel logo"
        src="/img/rbellog.png"
        class="rbel-logo"
      />
      Tiger Proxy Log
      <a
        v-if="localProxyWebUiUrl"
        id="test-rbel-webui-url"
        :href="`${localProxyWebUiUrl}`"
        target="_blank"
      >
        <i class="fa-solid fa-up-right-from-square" title="pop out pane"></i>
      </a>
    </h2>
    <div
      v-if="localProxyWebUiUrl"
      class="overflow-y-hidden overflow-x-auto h-100 w-100"
    >
      <iframe
        id="rbellog-details-iframe"
        ref="rbelLogIframe"
        allow="clipboard-write"
        class="h-100 w-100"
        :style="iframeStyle"
        :src="`${localProxyWebUiUrl}?embedded`"
        title="Rbel log view"
      />
    </div>
    <div v-else class="w-100 no-connection-local-proxy serverstatus-stopped">
      <i class="fas fa-project-diagram left"></i>
      No connection to local proxy.
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, type CSSProperties, inject, ref, watch } from "vue";
import { setupDragDetector } from "@/mouse/clickDragDetector.ts";
import type { Emitter } from "mitt";

defineProps<{
  localProxyWebUiUrl: string;
}>();

const detailsPane = ref<HTMLElement | null>(null);
const detailsResizer = ref<HTMLElement | null>(null);
const rbelLogIframe = ref<HTMLIFrameElement | null>(null);
const iFramePointerEventsActive = ref(true);
const emitter: Emitter<any> = inject("emitter") as Emitter<any>;

const expanded = ref(false);
const paneWidth = ref(0);

const tooltipExpandMinimize = computed(
  () => `${expanded.value ? "Minimize" : "Expand"} Tiger Proxy Log`,
);

const paneStyle = computed<CSSProperties>(() => ({
  width: `${paneWidth.value}px`,
  pointerEvents: iFramePointerEventsActive.value ? "auto" : "none",
  right: "0",
}));

const resizerStyle = computed<CSSProperties>(() => {
  return {
    width: "10px",
    right: `${paneWidth.value}px`,
    cursor: "col-resize",
    userSelect: "none",
  };
});

const iframeStyle = computed<CSSProperties>(() => ({
  pointerEvents: iFramePointerEventsActive.value ? "auto" : "none",
}));

function onClick(_ev: MouseEvent) {
  if (expanded.value) {
    paneWidth.value = 0;
    expanded.value = false;
  } else {
    expandToMiddle();
  }
}

function expandToMiddle() {
  if (detailsPane.value && detailsPane.value.parentElement) {
    paneWidth.value = detailsPane.value.parentElement.clientWidth / 2;
  }
  expanded.value = true;
}

function onDrag(ev: MouseEvent) {
  if (ev.pageX === 0 && ev.pageY === 0) {
    // Ignore drag end event
    return;
  }
  if (detailsPane.value) {
    const windowWidth = window.innerWidth;
    paneWidth.value = Math.max(windowWidth - ev.clientX, 0);
    expanded.value = paneWidth.value !== 0;
  }
}

//When dragging, if the mouse goes over the iframe, we cannot capture anymore
//the mouseup event to stop the dragging. To prevent this, we disable pointer events on the iframe while dragging.
function onDragStart() {
  iFramePointerEventsActive.value = false;
}

function onDragEnd() {
  iFramePointerEventsActive.value = true;
}

emitter.on("scrollToRbelLogMessage", (messageUuid: string) => {
  scrollToMessage(messageUuid);
});

function scrollToMessage(messageUuid: string) {
  if (!expanded.value) {
    expandToMiddle();
  }

  if (rbelLogIframe.value) {
    const basePath =
      rbelLogIframe.value.src.split("#").at(0) ?? rbelLogIframe.value.src;
    rbelLogIframe.value.src = `${basePath}#${messageUuid}`;
  }
}

watch(detailsResizer, () => {
  if (detailsResizer.value) {
    setupDragDetector(detailsResizer.value, onDrag, onDragStart, onDragEnd);
  }
});
</script>
<style scoped>
#rbellog_resize {
  right: 2px;
  top: 0;
  bottom: 0;
  width: 16px;
  z-index: 1050;
  border-left: 1px solid var(--gem-primary-400);
  background: var(--gem-primary-100);
  display: flex;
  align-items: flex-start;
}

#rbellog_resize div.resizer-right {
  top: 1px;
  left: -50px;
  right: 5px;
  color: var(--gem-primary-400);
  border: 1px solid var(--gem-primary-400);
  border-radius: 0.5rem;
  padding: 0.5rem;
  background: inherit;
  position: relative;
  cursor: inherit;
}

#rbellog_details_pane {
  display: flex;
  flex-direction: column;
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  top: 0;
  bottom: 0;
  z-index: 1050;
}

#rbellog-details-iframe > iframe {
  overflow: hidden;
}

.rbel-logo {
  width: 50px;
  margin-left: 0.5rem;
}

#rbellog_details_pane > h2 i {
  margin-left: 1rem;
  font-size: 50%;
  vertical-align: top;
  color: var(--gem-primary-400);
}

.no-connection-local-proxy {
  height: 15rem;
  background: white;
  text-align: center;
  line-height: 15rem;
}
</style>
