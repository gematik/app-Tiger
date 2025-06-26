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
<template>
  <div
    id="rbellog_resize"
    class="position-fixed"
    @mouseenter="ui.mouseEnterHandler"
    @mousedown="ui.mouseDownHandler"
    @mouseleave="ui.mouseLeaveHandler"
  >
    <i
      id="test-webui-slider"
      :title="tooltipExpandMinimize"
      class="fs-1 fa-solid fa-angles-left resizer-right"
      @click="clickedToggleIcon"
    ></i>
  </div>
  <div
    id="rbellog_details_pane"
    ref="detailsPane"
    class="d-none position-fixed pl-3 pt-3"
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
        allow="clipboard-write"
        class="h-100 w-100"
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
import Ui from "@/types/ui/Ui";
import { ref } from "vue";

const props = defineProps<{
  localProxyWebUiUrl: string;
  ui: Ui;
}>();

const detailsPane = ref<HTMLElement | null>(null);
const tooltipExpandMinimize = ref<string>("Expand Tiger Proxy Log");

function isMinimized(): boolean {
  return (
    detailsPane.value == undefined ||
    detailsPane.value.classList.contains("d-none")
  );
}

const clickedToggleIcon = (ev: MouseEvent) => {
  props.ui.toggleRightSideBar(ev);
  tooltipExpandMinimize.value = createTooltip();
};
const createTooltip = () => {
  return (isMinimized() ? "Expand" : "Minimize") + " Tiger Proxy Log";
};
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
}

#rbellog_resize i.resizer-right {
  top: 1px;
  left: -50px;
  right: 5px;
  color: var(--gem-primary-400);
  border: 1px solid var(--gem-primary-400);
  border-radius: 0.5rem;
  padding: 0.5rem;
  background: inherit;
  position: relative;
}

#rbellog_details_pane {
  display: flex;
  flex-direction: column;
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  top: 0;
  bottom: 0;
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
