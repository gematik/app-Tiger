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
import Sidebar from "./components/Sidebar.vue";
import { onBeforeUnmount, onMounted, provide, ref, type Ref, watchEffect } from "vue";
import MessageList from "./components/MessageList.vue";
import { messageQueueSymbol, useMessageQueue } from "@/api/MessageQueue.ts";
import { useProxyController } from "@/api/ProxyController.ts";
import { toastSymbol, useToast } from "./Toast.ts";
import RbelFilterModal from "./components/RbelFilterModal.vue";
import { rbelFilterSymbol, useRbelFilter } from "@/api/RbelFilter.ts";
import { rbelQueryModalSymbol, useRbelQueryModal } from "./RbelQueryModal.ts";
import { settingsSymbol, useSettings } from "./Settings.ts";
import { rawContentModalSymbol, useRawContentModal } from "./RawContentModal.ts";
import RawContentModal from "@/components/RawContentModal.vue";
import SearchModal from "@/components/SearchModal.vue";
import StatusHeader from "@/components/StatusHeader.vue";
import SettingsHeader from "@/components/SettingsHeader.vue";
import RbelQueryModal from "@/components/RbelQueryModal.vue";
import RouteModal from "@/components/RouteModal.vue";
import ExportModal from "@/components/ExportModal.vue";

const settings = useSettings();
provide(settingsSymbol, settings);

const rbelQuery = useRbelQueryModal();
provide(rbelQueryModalSymbol, rbelQuery);

const rawContent = useRawContentModal();
provide(rawContentModalSymbol, rawContent);

const rbelFilter = useRbelFilter();
provide(rbelFilterSymbol, rbelFilter);

const toastElement: Ref<HTMLElement | null> = ref(null);
const toast = useToast(toastElement);
provide(toastSymbol, toast);

const messageQueue = useMessageQueue(settings.reverseMessageQueue, rbelFilter.rbelPath, {
  onError: (errMsg: string) => {
    if (!toast.isShown()) {
      toast.showToast(errMsg);
    }
  },
});
provide(messageQueueSymbol, messageQueue);

const proxyController = useProxyController({
  onError: (errMsg: string) => {
    toast.showToast(errMsg);
  },
});

onMounted(() => {
  // this workaround is necessary to capture `scrollToMessage` events from the messages
  function scrollToMessage(uuid: string) {
    messageQueue.scrollToMessage(uuid);
  }

  (window as any).scrollToMessage = scrollToMessage;
});

onBeforeUnmount(() => {
  delete (window as any).scrollToMessage;
});

// Handle Embedding
const query = new URLSearchParams(window.location.search);
const isEmbedded = query.has("embedded");

const hash = ref(window.location.hash);

watchEffect(() => {
  window.addEventListener("hashchange", () => {
    hash.value = window.location.hash;
    messageQueue.scrollToMessage(hash.value.slice(1));
  });
});
</script>

<template>
  <div>
    <!-- Error Toasts -->
    <div class="position-fixed top-0 end-0 p-3" style="z-index: 2000">
      <div
        ref="toastElement"
        class="toast align-items-center text-white bg-danger border-0"
        role="alert"
        aria-live="assertive"
        aria-atomic="true"
      >
        <div class="d-flex">
          <div class="toast-body">
            {{ toast.message }}
          </div>
          <button
            type="button"
            class="btn-close btn-close-white me-2 m-auto"
            data-bs-dismiss="toast"
            aria-label="Close"
          />
        </div>
      </div>
    </div>
    <!-- Filter Modal -->
    <RbelFilterModal v-if="__IS_ONLINE_MODE__" />
    <!-- Jexl Query Modal -->
    <RbelQueryModal v-if="__IS_ONLINE_MODE__" />
    <!-- Raw Content of Message Modal -->
    <RawContentModal />
    <!-- Search Modal -->
    <SearchModal />
    <!-- Proxy Route Modal -->
    <RouteModal v-if="__IS_ONLINE_MODE__" />
    <!-- Export Modal -->
    <ExportModal v-if="__IS_ONLINE_MODE__" />
    <div class="d-flex">
      <Sidebar
        v-if="!isEmbedded"
        class="flex-grow-1"
        :on-click-reverse="(reversed) => (settings.reverseMessageQueue.value = reversed)"
        :on-click-quit-proxy="proxyController.quitProxy"
        :on-click-reset-messages="proxyController.resetMessageQueue"
      />
      <div class="d-flex flex-column flex-grow-2 vh-100">
        <div class="d-flex flex-row">
          <SettingsHeader
            v-if="isEmbedded"
            :no-logo="true"
            :class="[isEmbedded ? 'pe-3' : 'pe-5']"
            :message-queue="messageQueue"
            :on-click-reset-messages="proxyController.resetMessageQueue"
            :on-click-quit-proxy="proxyController.quitProxy"
          />
          <StatusHeader class="flex-grow-1" />
        </div>
        <MessageList class="flex-grow-1 flex-shrink-1" :is-embedded="isEmbedded" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.flex-grow-2 {
  flex-grow: 2;
}
</style>
