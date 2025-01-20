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
  <div
    v-if="quitTestrunOngoing"
    id="workflow-messages"
    class="alert banner-message fade show py-5 text-danger text-center fade show test-messages-quit"
    role="alert"
  >
    <i class="btn-banner-close fa-solid fa-xmark" @click="closeWindow"></i>
    <h4 class="pt-3 pb-0">
      <i class="fa-2xl fa-solid fa-power-off pe-3"></i>
      {{ quitReason.message }}
    </h4>
    <div class="fs-5">Please check console</div>
    <div v-if="quitReason.details" class="fs-6">{{ quitReason.details }}</div>
  </div>
  <div
    v-else
    id="workflow-messages"
    :class="`alert banner-message fade ${bannerMessage ? 'show' : ''}`"
    role="alert"
  >
    <i
      v-if="isOfType(BannerType.MESSAGE)"
      class="btn-banner-close fa-solid fa-xmark"
      @click="closeWindow"
    ></i>
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
    <div
      v-if="isOfType(BannerType.STEP_WAIT)"
      class="btn btn-success w-100 mt-3 mb-1"
      @click="confirmContinue"
    >
      Continue
    </div>
    <div
      v-if="isOfType(BannerType.FAIL_PASS)"
      class="row justify-content-around"
    >
      <div class="btn btn-success w-45 mt-3 mb-1" @click="confirmContinue">
        Pass
      </div>
      <div class="btn btn-danger w-45 mt-3 mb-1" @click="confirmFail">Fail</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BannerMessage from "@/types/BannerMessage";
import BannerType from "@/types/BannerType";

import { onUpdated } from "vue";
import QuitReason from "@/types/QuitReason";

const props = defineProps<{
  bannerMessage: BannerMessage | boolean;
  quitTestrunOngoing: boolean;
  quitReason: QuitReason;
}>();

function isOfType(bannerType: BannerType): boolean {
  return (
    props.bannerMessage &&
    (props.bannerMessage as BannerMessage).type === bannerType
  );
}

// after close via clicking on button we need to show it again if new data is sent
onUpdated(() => {
  document.getElementById("workflow-messages")!.classList.toggle("show", true);
});

function closeWindow() {
  document.getElementById("workflow-messages")!.classList.remove("show");
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
