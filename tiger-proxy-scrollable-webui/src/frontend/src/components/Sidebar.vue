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
import { computed, inject, type Ref, ref, toRaw } from "vue";
import { messageQueueSymbol } from "@/api/MessageQueue.ts";
import { useVirtualList } from "@vueuse/core";
import MessageItem from "./MessageItem.vue";
import SettingsHeader from "./SettingsHeader.vue";

const props = defineProps<{
  onClickReverse: (reversed: boolean) => void;
  onClickResetMessages: () => void;
  onClickQuitProxy: () => void;
}>();

const messageQueue = inject(messageQueueSymbol)!;
const messages = messageQueue.messagesMeta;

// Items differ in height (additional info strings), so heights are tracked per message. A single shared
// height flips between values as differently sized items scroll in, and every flip moves the rendered
// window, which mounts other items that flip it back.
const measuredHeights = new Map<string, number>();
const measuredHeightsVersion = ref(0);
const estimatedItemHeight: Ref<number> = ref(10);

// useVirtualList walks the whole list on every height change. The overview is replaced, never mutated, so
// handing it the raw array avoids tracking each element on every walk.
const rawMessages = computed(() => toRaw(messages.value));
const itemHeights = computed(() => {
  void measuredHeightsVersion.value;
  const estimate = estimatedItemHeight.value;
  return rawMessages.value.map((message) => measuredHeights.get(toRaw(message).uuid) ?? estimate);
});

const { list, containerProps, wrapperProps } = useVirtualList(rawMessages, {
  itemHeight: (index) => itemHeights.value[index] ?? estimatedItemHeight.value,
});

function onItemHeightChanged(uuid: string, height: number) {
  if (measuredHeights.get(uuid) === height) return;
  measuredHeights.set(uuid, height);
  if (measuredHeights.size === 1 || height < estimatedItemHeight.value)
    estimatedItemHeight.value = height;
  measuredHeightsVersion.value++;
}

const revMsgQueue = messageQueue.reversedMessageQueue;
</script>

<template>
  <div class="sidebar d-flex flex-row">
    <div class="flex-grow-1"></div>
    <div class="vh-100 flex-shrink-1 flex-grow-0 d-flex flex-column">
      <div v-bind="containerProps" class="vh-100">
        <SettingsHeader
          class="sticky-top sticky-header"
          :message-queue="messageQueue"
          :on-click-reverse="props.onClickReverse"
          :on-click-reset-messages="props.onClickResetMessages"
          :on-click-quit-proxy="props.onClickQuitProxy"
        />
        <div v-bind="wrapperProps">
          <MessageItem
            v-for="message in list"
            class="sidebar-item p-1 ps-2 pe-4 py-2"
            :class="[
              message.data.request
                ? revMsgQueue
                  ? 'border-bottom'
                  : ''
                : revMsgQueue
                  ? ''
                  : 'border-bottom',
            ]"
            :on-height-changed="(height: number) => onItemHeightChanged(message.data.uuid, height)"
            :key="message.data.uuid"
            :message="message.data"
            :on-click="() => messageQueue.scrollToMessage(message.data.uuid)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.sticky-header {
  background: var(--gem-neutral-050);
}

.sidebar {
  background: var(--gem-neutral-050);
}

.sidebar-item {
  max-width: 360px;
  min-width: 360px;

  &:hover {
    background: var(--gem-neutral-200);

    .sequence-number {
      background: var(--gem-neutral-050);
    }
  }
}
</style>
