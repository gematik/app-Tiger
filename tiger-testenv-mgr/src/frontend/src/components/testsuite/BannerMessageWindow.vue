<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div v-if="quitTestrunOngoing && !shutdownTestrunOngoing" id="workflow-messages"
       class="alert banner-message fade show py-5 text-danger text-center fade show test-messages-quit" role="alert">
    <i class="btn-banner-close fa-solid fa-xmark" v-on:click="closeWindow"></i>
    <h4 class="pt-3 pb-0">
      <i class="fa-2xl fa-solid fa-power-off pe-3"></i>
      Quit on user request!
    </h4>
    <div>Please check console</div>
  </div>
  <div v-else id="workflow-messages"
       :class="`alert banner-message fade ${bannerMessage ? 'show' : ''}`" role="alert">
    <i v-if="isOfType(BannerType.MESSAGE)"
       class="btn-banner-close fa-solid fa-xmark" v-on:click="closeWindow"></i>
    <h4 class="pt-3 pb-0 text-center">
      <i class="fa-solid fa-bullhorn fa-flip-horizontal"></i>
      Workflow message
    </h4>
    <div v-if="bannerMessage" class="banner">
      <div v-if="(bannerMessage as BannerMessage).isHtml">
        <div v-html="`${(bannerMessage as BannerMessage).text}`"></div>
      </div>
      <div v-else :style="`color: ${(bannerMessage as BannerMessage).color};`">
        {{ (bannerMessage as BannerMessage).text }}
      </div>
    </div>
    <div v-if="isOfType(BannerType.STEP_WAIT)"
         v-on:click="confirmContinue"
         class="btn btn-success w-100 mt-3 mb-1">
      Continue
    </div>
    <div class="row justify-content-around" v-if="isOfType(BannerType.FAIL_PASS)">
      <div v-on:click="confirmContinue" class="btn btn-success w-45 mt-3 mb-1">
        Pass
      </div>
      <div v-on:click="confirmFail" class="btn btn-danger w-45 mt-3 mb-1">
        Fail
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BannerMessage from "@/types/BannerMessage";
import BannerType from "@/types/BannerType";

import {inject, onUpdated} from "vue";
import {Emitter} from "mitt";

const emitter: Emitter<any> = inject('emitter') as Emitter<any>;

const props = defineProps<{
  bannerMessage: BannerMessage | boolean;
  quitTestrunOngoing: boolean;
  shutdownTestrunOngoing: boolean;
}>();

function isOfType(bannerType: BannerType): boolean {
  return props.bannerMessage && (props.bannerMessage as BannerMessage).type === bannerType;
}

// after close via clicking on button we need to show it again if new data is sent
onUpdated(() => {
  document.getElementById('workflow-messages')!.classList.toggle('show', true);
});

function closeWindow() {
  document.getElementById('workflow-messages')!.classList.toggle('show');
}

function confirmShutdownPressed() {
  emitter.emit('confirmShutdownPressed');

  fetch(import.meta.env.BASE_URL + "status/confirmShutdown")
  .then((response) => response.text())
  .then(() => {
    closeWindow();
    alert("Backend of Workflow UI has been shut down!\nRbelLog details pane has no more filtering / search support!");
  });
}

function confirmContinue() {
  fetch(import.meta.env.BASE_URL + "status/continueExecution")
  .then((response) => response.text())
  .then(() => {
    closeWindow();
  });
}

function confirmFail() {
  fetch(import.meta.env.BASE_URL + "status/failExecution")
  .then((response) => response.text())
  .then(() => {
    // do nothing as the resume message will appear shortly after the click
  });
}
</script>

<style scoped>
#workflow-messages {
  position: fixed;
  bottom: 0;
  margin: 0;
  width: 50%;
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
