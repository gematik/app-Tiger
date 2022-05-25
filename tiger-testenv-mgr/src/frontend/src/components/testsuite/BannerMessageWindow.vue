<!--
  - Copyright (c) 2022 gematik GmbH
  - 
  - Licensed under the Apache License, Version 2.0 (the License);
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  - 
  -     http://www.apache.org/licenses/LICENSE-2.0
  - 
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an 'AS IS' BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div id="workflow-messages" :class="`alert banner-message fade ${bannerData.length > 0 ? 'show' : ''}`" role="alert">
    <i v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type !== BannerType.TESTRUN_ENDED"
       class="btn-banner-close fa-solid fa-xmark" v-on:click="closeWindow"></i>
    <h4 class="pt-3 pb-0 text-center">
      <i class="fa-solid fa-bullhorn fa-flip-horizontal"></i>
      Workflow message
    </h4>
    <div v-if="bannerData.length > 0" :style="`color: ${bannerData[bannerData.length-1].color};`" class="banner">
      {{ bannerData[bannerData.length - 1].text }}
    </div>
    <div v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.TESTRUN_ENDED"
         v-on:click="sendQuit"
         class="btn btn-danger w-100 mt-3 mb-1">
      Quit
    </div>
  </div>
</template>

<script setup lang="ts">
import BannerMessage from "@/types/BannerMessage";
import BannerType from "@/types/BannerType";
import {onUpdated} from "vue";

onUpdated(() => {
  document.getElementById('workflow-messages')!.classList.toggle('show', true);
});

defineProps<{
  bannerData: BannerMessage[];
}>();

function closeWindow(ev : MouseEvent) {
  document.getElementById('workflow-messages')!.classList.toggle('show');
}

function sendQuit(event: MouseEvent) {
  closeWindow(event);
  fetch(process.env.BASE_URL + "status/quit")
  .then((response) => response.text())
  .then((data) => {
    alert("Backend of Workflow UI has been shut down!\nRbelLog details pane has no more filtering / search support!");
  });
}
</script>

<style scoped>
#workflow-messages {
  position: fixed;
  bottom: 0;
  margin: 0;
  width: 50%;
  z-index: 20000;
  padding: 0.5rem;
  background: rgba(234, 236, 245, 0.9); /* --gem-primary-400) */
  border: 1px solid var(--gem-primary-400);
  color: var(--gem-primary-400);
  transition: opacity 0.75s ease-in-out !important;
}

.banner {
  font-weight: bolder;
  font-size: 150%;
  margin-bottom: 0;
  text-align: center;
  padding: 0.5rem;
}

.btn-banner-close {
  color: var(--gem-primary-400);
  border-radius: 0.5rem;
  padding: 0.75rem;
  background: var(--gem-primary-025);
  float: right;
  cursor: pointer;
}
</style>
