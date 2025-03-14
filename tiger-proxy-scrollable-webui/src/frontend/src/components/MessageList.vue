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
import { inject, type Ref, ref, triggerRef, watch } from "vue";
import { messageQueueSymbol } from "@/api/MessageQueue.ts";
import Message from "./Message.vue";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { useDropZone, useFileDialog } from "@vueuse/core";
import { useProxyController } from "@/api/ProxyController.ts";
import { toastSymbol } from "../Toast.ts";
import { DynamicScroller, DynamicScrollerItem } from "vue-virtual-scroller";
import "vue-virtual-scroller/dist/vue-virtual-scroller.css";
import { settingsSymbol } from "../Settings.ts";
import { faCloudUploadAlt } from "@fortawesome/free-solid-svg-icons";

defineProps<{ isEmbedded: boolean }>();

const messageQueue = inject(messageQueueSymbol)!;
const settings = inject(settingsSymbol)!;

const dynamicScrollerRef = ref<typeof DynamicScroller | null>(null);

watch(dynamicScrollerRef, () => {
  messageQueue.internal.ref.value = dynamicScrollerRef.value;
});

const messageItemSizeRef = ref<null>();

function triggerMessageItemSize() {
  triggerRef(messageItemSizeRef);
}

const errorToast = inject(toastSymbol);

const isUploadInProgress: Ref<boolean> = ref(false);

const proxyController = useProxyController({
  onError: (errMsg: string) => {
    errorToast?.showToast(errMsg);
  },
  onLoading: (isLoading: boolean) => {
    isUploadInProgress.value = isLoading;
  },
});

const dropZoneRef = ref<HTMLDivElement | null>(null);

const { isOverDropZone } = useDropZone(dropZoneRef, {
  onDrop: async (files: File[] | null) => {
    if (files) {
      await proxyController.importRbelLogFile({ rbelFileContent: await files[0].text() });
    }
  },
  multiple: false,
  preventDefaultForUnhandled: false,
});

const { files, open } = useFileDialog({
  multiple: false,
  accept: "*/*",
});

watch(files, async (newFiles) => {
  if (newFiles) {
    await proxyController.importRbelLogFile({ rbelFileContent: await newFiles[0].text() });
  }
});
</script>

<template>
  <DynamicScroller
    v-if="messageQueue.internal.messages.value.length > 0"
    ref="dynamicScrollerRef"
    :items="messageQueue.internal.messages.value"
    :min-item-size="200"
    :emit-update="true"
    keyField="uuid"
    class="h-100 overflow-y-scroll scroll-container"
    @update="messageQueue.internal.update"
  >
    <template #default="{ item, active }">
      <DynamicScrollerItem
        :item="item"
        :active="active"
        :size-dependencies="[
          item,
          messageItemSizeRef,
          settings.hideMessageHeaders,
          settings.hideMessageDetails,
        ]"
        class="message"
      >
        <Message
          :message="item"
          :key="item.uuid + item.type"
          :on-toggle-details-or-header="triggerMessageItemSize"
        />
      </DynamicScrollerItem>
    </template>
  </DynamicScroller>
  <div v-else class="h-auto">
    <div
      v-if="!isEmbedded"
      :class="['container', 'mt-5', isUploadInProgress ? 'disabled' : '']"
      ref="dropZoneRef"
    >
      <div class="drop-zone border rounded p-4 text-center" :class="{ 'bg-light': isOverDropZone }">
        <div class="d-flex flex-column gap-2 align-items-center">
          <h3 class="mb-3">Feed the Tiger</h3>
          <FontAwesomeIcon
            v-if="!isUploadInProgress"
            :icon="faCloudUploadAlt"
            size="3x"
            class="text-primary mb-3"
          />
          <div v-if="isUploadInProgress" class="text-primary spinner-border mb-3" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p>Drop a <strong>*.tgr</strong> log file here</p>
          <p>- or -</p>
          <button type="button" class="btn btn-outline-primary" @click="() => open()">
            Browse Files
          </button>
        </div>
      </div>
    </div>
    <div v-else class="mt-5 text-center">Loading...</div>
  </div>
</template>

<style scoped lang="scss">
.container {
  height: 100%;
}

// hack for embedded mode; otherwise the container is not scrollable by mouse
.scroll-container {
  transform: translateZ(0);
  will-change: transform;
}

.disabled {
  pointer-events: none;

  p,
  h3,
  button {
    opacity: 0.1;
  }
}

.vue-recycle-scroller__item-wrapper {
  overflow-y: auto;
}
</style>
