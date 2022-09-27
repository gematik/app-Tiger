<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<template>
  <div id="workflow-messages" :class="`alert banner-message fade ${bannerData.length > 0 ? 'show' : ''}`" role="alert">
    <i v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type == BannerType.MESSAGE"
       class="btn-banner-close fa-solid fa-xmark" v-on:click="closeWindow"></i>
    <h4 class="pt-3 pb-0 text-center">
      <i class="fa-solid fa-bullhorn fa-flip-horizontal"></i>
      Workflow message
    </h4>
    <div v-if="bannerData.length > 0" class="banner">
      <div v-if="bannerData[bannerData.length-1].isHtml">
        <div v-html="`${bannerData[bannerData.length - 1].text}`"></div>
      </div>
      <div v-else :style="`color: ${bannerData[bannerData.length-1].color};`">
        {{ bannerData[bannerData.length - 1].text }}
      </div>
    </div>
    <div v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.TESTRUN_ENDED"
         v-on:click="sendQuit"
         class="btn btn-danger w-100 mt-3 mb-1">
      Quit
    </div>
    <div v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.STEP_WAIT"
         v-on:click="sendContinue"
         class="btn btn-success w-100 mt-3 mb-1">
      Continue
    </div>
    <div class="row justify-content-around" v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.FAIL_PASS">
        <div v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.FAIL_PASS"
             v-on:click="sendContinue"
             class="btn btn-success w-45 mt-3 mb-1">
          Pass
        </div>
        <div v-if="bannerData.length > 0 && bannerData[bannerData.length-1].type === BannerType.FAIL_PASS"
             v-on:click="sendFail"
             class="btn btn-danger w-45 mt-3 mb-1">
          Fail
        </div>
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
  fetch(process.env.BASE_URL + "status/quit")
  .then((response) => response.text())
  .then((data) => {
    closeWindow(event);
    alert("Backend of Workflow UI has been shut down!\nRbelLog details pane has no more filtering / search support!");
  });
}

function sendContinue(event: MouseEvent) {
  fetch(process.env.BASE_URL + "status/continueExecution")
  .then((response) => response.text())
  .then((data) => {
    // do nothing as the resume message will appear shortly after the click
  });
}

function sendFail(event: MouseEvent) {
  fetch(process.env.BASE_URL + "status/failExecution")
  .then((response) => response.text())
  .then((data) => {
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
