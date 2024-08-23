<!--
  - Copyright 2024 gematik GmbH
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
  -->

<template>
  <div class="position-fixed" id="rbellog_resize"
       v-on:mouseenter="ui.mouseEnterHandler"
       v-on:mousedown="ui.mouseDownHandler"
       v-on:mouseleave="ui.mouseLeaveHandler">
    <i v-on:click="ui.toggleRightSideBar" class="fa-solid fa-angles-left resizer-right" id="test-webui-slider"></i>
  </div>
  <div class="d-none position-fixed pl-3 pt-3" id="rbellog_details_pane">
    <h2>
      <img alt="RBel logo" src="/img/rbellog.png" class="rbel-logo" id="test-rbel-logo">
      Tiger Proxy Log
      <a v-if="localProxyWebUiUrl" :href="`${localProxyWebUiUrl}`" target="_blank" id="test-rbel-webui-url">
        <i class="fa-solid fa-up-right-from-square" title="pop out pane"></i>
      </a>
    </h2>
    <iframe v-if="localProxyWebUiUrl" id="rbellog-details-iframe" allow="clipboard-write"
            class="h-100 w-100"
            :src="`${localProxyWebUiUrl}?embedded=true`" title="Rbel log view"/>
    <div v-else class="w-100 no-connection-local-proxy serverstatus-stopped">
      <i class="fas fa-project-diagram left"></i>
      No connection to local proxy.
    </div>
  </div>
</template>
<script setup lang="ts">
import Ui from "@/types/ui/Ui";

defineProps<{
  localProxyWebUiUrl: string;
  ui: Ui
}>()
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
  left: -19px;
  right: 5px;
  color: var(--gem-primary-400);
  border: 1px solid var(--gem-primary-400);
  border-radius: 0.5rem;
  padding: 0.5rem;
  background: inherit;
  position: relative;
}

#rbellog_details_pane {
  background: var(--gem-primary-100);
  color: var(--gem-primary-400);
  top: 0;
  bottom: 0;
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