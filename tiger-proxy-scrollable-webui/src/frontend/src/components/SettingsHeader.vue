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
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { inject, onMounted, ref, type Ref } from "vue";
import { Dropdown } from "bootstrap";
import type { UseMessageQueueReturn } from "@/api/MessageQueue.ts";
import { settingsSymbol } from "../Settings.ts";

const props = defineProps<{
  messageQueue: UseMessageQueueReturn;
  onClickResetMessages: () => void;
  onClickQuitProxy: () => void;
}>();

const settings = inject(settingsSymbol)!;

const dropdownElement: Ref<HTMLElement | null> = ref(null);
const dropdownComponent: Ref<Dropdown | null> = ref(null);

const reversedQueueReversed = props.messageQueue.reversedMessageQueue;

onMounted(() => {
  if (dropdownElement.value) {
    dropdownComponent.value = new Dropdown(dropdownElement.value);
  }
});

function onClickConfigRoutesFn(ev: MouseEvent) {
  ev.preventDefault();
  dropdownComponent.value?.hide();
}

function onClickResetMessagesFn(ev: MouseEvent) {
  ev.preventDefault();
  props.onClickResetMessages();
  dropdownComponent.value?.hide();
}

function onClickQuitProxyFn(ev: MouseEvent) {
  ev.preventDefault();
  props.onClickQuitProxy();
  dropdownComponent.value?.hide();
}
</script>

<template>
  <div class="sticky-header p-2 border-bottom d-flex justify-content-between">
    <span class="logo text-nowrap">Tiger Proxy</span>
    <div class="p-1 flex-grow-1" />
    <button
      type="button"
      class="btn"
      @click="() => (settings.reverseMessageQueue.value = !reversedQueueReversed)"
    >
      <FontAwesomeIcon
        icon="fa-solid fa-arrow-down-1-9"
        title="Sort from oldest to newest"
        v-if="reversedQueueReversed"
      />
      <FontAwesomeIcon
        icon="fa-solid fa-arrow-up-1-9"
        title="Sort from newest to oldest"
        v-if="!reversedQueueReversed"
      />
    </button>
    <div class="border-end m-1" />
    <div ref="dropdownElement" class="dropdown">
      <button
        type="button"
        class="btn dropdown-toggle"
        data-bs-toggle="dropdown"
        aria-expanded="false"
        data-bs-auto-close="outside"
        title="Settings"
      >
        <FontAwesomeIcon icon="fa-solid fa-gear" />
      </button>
      <form class="dropdown-menu p-4">
        <div class="mb-3">
          <label class="form-label bold"> Message Options </label>
          <div class="form-check">
            <input
              v-model="settings.hideMessageHeaders.value"
              type="checkbox"
              class="form-check-input"
              id="hideHeader"
            />
            <label class="form-check-label" for="hideHeader"> Hide Header </label>
          </div>
          <div class="form-check">
            <input
              v-model="settings.hideMessageDetails.value"
              type="checkbox"
              class="form-check-input"
              id="hideDetails"
            />
            <label class="form-check-label" for="hideDetails"> Hide Details </label>
          </div>
        </div>
        <div class="border-bottom mb-3" />
        <button
          data-bs-toggle="modal"
          data-bs-target="#routeModal"
          @click="onClickConfigRoutesFn"
          class="btn btn-secondary w-100 mb-2"
        >
          <FontAwesomeIcon icon="fa-solid fa-route" />&nbsp;Configure Routes
        </button>
        <button @click="onClickResetMessagesFn" class="btn btn-danger w-100 mb-2">
          <FontAwesomeIcon icon="fa-solid fa-trash-can" />&nbsp;Reset Messages
        </button>
        <button @click="onClickQuitProxyFn" class="btn btn-outline-secondary w-100">
          <FontAwesomeIcon icon="fa-solid fa-power-off" />&nbsp;Quit Proxy
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.logo {
  font-family: noto-sans, sans-serif;
  font-weight: 600;
  font-size: 22px;
  line-height: 1.5em;
  color: var(--gem-primary-700);
}

.logo::before {
  content: "";
  display: inline-block;
  background: url("../assets/tiger-mono-bg.svg") no-repeat center center;
  background-size: contain;
  width: 1.7em;
  height: 1.7em;
  vertical-align: middle;
  margin-right: 12px;
  border-radius: 4px;
}

.dropdown-menu {
  min-width: 300px;
}
</style>
