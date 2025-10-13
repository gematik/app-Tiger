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
import { computed, onMounted, provide, readonly, ref, type Ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { rawContentModalSymbol, useRawContentModal } from "@/RawContentModal.ts";
import { settingsSymbol, useSettings } from "@/Settings.ts";
import { toastSymbol, useToast } from "@/Toast.ts";
import { rbelQueryModalSymbol, useRbelQueryModal } from "@/RbelQueryModal.ts";
import { useProxyController } from "@/api/ProxyController.ts";
import RawContentModal from "@/components/RawContentModal.vue";
import Message from "@/components/Message.vue";
import RbelQueryModal from "@/components/RbelQueryModal.vue";
import SettingsHeader from "@/components/SettingsHeader.vue";
import type { Message as MessageType, UseMessageQueueReturn } from "@/api/MessageQueue.ts";

const route = useRoute();
const router = useRouter();

const settings = useSettings();
provide(settingsSymbol, settings);

const rawContent = useRawContentModal();
provide(rawContentModalSymbol, rawContent);

const rbelQuery = useRbelQueryModal();
provide(rbelQueryModalSymbol, rbelQuery);

const toastElement: Ref<HTMLElement | null> = ref(null);
const toast = useToast(toastElement);
provide(toastSymbol, toast);

const proxyController = useProxyController({
  onError: (errMsg: string) => {
    toast.showToast(errMsg);
  },
});

const selectedMessage: Ref<MessageType | null> = ref(null);
const isLoading = ref(false);

const isOnlineMode = __IS_ONLINE_MODE__;

// Create a mock message queue for the SettingsHeader component
// Since we're in single message mode, we don't have a real message queue
function createMockMessageQueue(): UseMessageQueueReturn {
  return {
    reversedMessageQueue: readonly(ref(false)),
    messagesMeta: computed(() => []),
    total: computed(() => (selectedMessage.value ? 1 : 0)),
    scrollToMessage: () => {},
    reset: () => {},
    internal: {
      update: () => {},
      messages: ref([]) as Ref<MessageType[]>,
      ref: ref(null),
    },
  };
}

const mockMessageQueue = createMockMessageQueue();

async function fetchMessageByUuid(uuid: string) {
  isLoading.value = true;
  try {
    const messageData = await proxyController.getFullyRenderedMessage({ uuid });

    if (messageData) {
      selectedMessage.value = {
        type: "loaded",
        htmlContent: messageData.content,
        index: 0, // Index is not relevant for single message view
        uuid: messageData.uuid,
        sequenceNumber: messageData.sequenceNumber,
      };
    } else {
      toast.showToast("Failed to load message content");
    }
  } catch (error) {
    toast.showToast(`Error loading message: ${error}`);
  } finally {
    isLoading.value = false;
  }
}

function navigateToPartnerMessage(partnerUuid: string) {
  try {
    router.push(`/message/${partnerUuid}`);
  } catch (error) {
    toast.showToast(`Navigation error: ${error}`);
  }
}

function setupScrollToMessage() {
  (window as any).scrollToMessage = (partnerUuid: string) => {
    navigateToPartnerMessage(partnerUuid);
  };
}

onMounted(() => {
  setupScrollToMessage();

  const messageUuid = route.params.uuid as string;
  if (messageUuid) {
    fetchMessageByUuid(messageUuid);
  } else {
    toast.showToast("No message UUID provided");
  }
});

watch(
  () => route.params.uuid,
  (newUuid, oldUuid) => {
    if (newUuid && newUuid !== oldUuid) {
      fetchMessageByUuid(newUuid as string);
    }
  },
);
</script>

<template>
  <div class="single-message-page">
    <!-- Settings Header -->
    <SettingsHeader
      :message-queue="mockMessageQueue"
      :on-click-reset-messages="proxyController.resetMessageQueue"
      :on-click-quit-proxy="proxyController.quitProxy"
      :no-logo="false"
    />

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

    <!-- Raw Content Modal -->
    <RawContentModal />

    <!-- Jexl Query Modal -->
    <RbelQueryModal v-if="isOnlineMode" />

    <!-- Main Content -->
    <div class="container-fluid p-4">
      <div class="row">
        <div class="col-12">
          <div class="d-flex align-items-center mb-3">
            <button class="btn btn-outline-primary me-3" @click="$router.go(-1)" title="Go back">
              <i class="fas fa-arrow-left"></i> Back
            </button>
            <h2 class="mb-0">Single Message View</h2>
          </div>

          <div
            v-if="isLoading"
            class="d-flex justify-content-center align-items-center"
            style="min-height: 200px"
          >
            <div class="text-center">
              <div class="spinner-border text-primary mb-3" role="status">
                <span class="visually-hidden">Loading...</span>
              </div>
              <p class="text-muted">Loading message...</p>
            </div>
          </div>

          <div v-else-if="selectedMessage" class="message-container">
            <Message :message="selectedMessage" :on-toggle-details-or-header="() => {}" />
          </div>

          <div v-else class="alert alert-warning">
            <h4>Message not found</h4>
            <p>
              The requested message could not be loaded. Please check the message ID and try again.
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.single-message-page {
  min-height: 100vh;
  background-color: #f8f9fa;
}

.message-container {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}
</style>
