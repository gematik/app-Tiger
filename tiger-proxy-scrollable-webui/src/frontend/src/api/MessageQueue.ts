///
///
/// Copyright 2025 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import type { GetAllMessagesDto, GetMessagesDto, MetaMessageDto } from "./MessageTypes.ts";
import {
  computed,
  type ComputedRef,
  type DeepReadonly,
  type InjectionKey,
  nextTick,
  onMounted,
  onUnmounted,
  readonly,
  ref,
  type Ref,
  watch,
} from "vue";
import { computedWithControl, useDebounceFn, useIntervalFn } from "@vueuse/core";
import { useProxyController, type UseProxyControllerOptions } from "@/api/ProxyController.ts";

type MessageBase = {
  type: "loading" | "error" | "loaded";
  index: number;
  uuid: string;
  sequenceNumber: number;
};

export type Message = MessageBase &
  (
    | {
        type: "loading";
      }
    | {
        type: "error";
        message: string;
      }
    | {
        type: "loaded";
        htmlContent: string;
      }
  );

export interface UseMessageQueueReturn {
  /**
   * Total number of messages available.
   */
  total: ComputedRef<number>;
  /**
   * Metadata for all messages. The array hosts all messages and therefore `messagesMeta.length === total`.
   */
  messagesMeta: ComputedRef<MetaMessageDto[]>;
  /**
   * Read only reactive boolean indicating if the message queue is reversed or not.
   */
  reversedMessageQueue: DeepReadonly<Ref<boolean>>;
  /**
   * Scroll to a message with `uuid`.
   */
  scrollToMessage: (uuid: string) => void;
  /**
   * Delete all fetched messages.
   */
  reset: () => void;
  /**
   * Internal properties to be set.
   */
  internal: {
    update: (
      startIndex: number,
      endIndex: number,
      visibleStartIndex: number,
      visibleEndIndex: number,
    ) => void;
    messages: Ref<Message[]>;
    ref: Ref<any | null>;
  };
}

export interface UseMessageQueueOptions extends UseProxyControllerOptions {}

export const messageQueueSymbol: InjectionKey<UseMessageQueueReturn> = Symbol("messageQueueSymbol");

export function useMessageQueue(
  reversedMessageQueue: Ref<boolean>,
  rbelFilter: Ref<string>,
  options: UseMessageQueueOptions,
): UseMessageQueueReturn {
  const proxyController = useProxyController(options);

  const latestMessageOverview: Ref<GetAllMessagesDto | null> = ref(null);
  const latestMessage: Ref<GetMessagesDto | null> = ref(null);

  const dynamicScrollerRef: Ref<any | null> = ref(null);

  const messagesMeta = computed(() => {
    const messages = latestMessageOverview.value?.messages ?? [];
    return reversedMessageQueue.value ? messages.toReversed() : messages;
  });

  const filterRbelPath = computedWithControl(rbelFilter, () => {
    const trimmed = rbelFilter.value.trim();
    return trimmed.length > 0 ? trimmed : undefined;
  });

  async function loadMessageOverview() {
    const oldResult = latestMessageOverview.value;
    const newResult = await proxyController.getMetaMessages({
      filterRbelPath: filterRbelPath.value,
    });
    // by preventing from setting unnecessarily a new value we keep side effects small
    if (
      newResult &&
      (oldResult?.totalFiltered !== newResult.totalFiltered || oldResult?.hash !== newResult.hash)
    ) {
      latestMessageOverview.value = newResult;
    }
  }

  watch(filterRbelPath, async (newRbelPath, oldRbelPath) => {
    if (newRbelPath !== oldRbelPath) {
      latestMessage.value = null;
      await loadMessageOverview();
    }
  });

  const { resume, pause } = useIntervalFn(
    async () => {
      await loadMessageOverview();
    },
    1000,
    { immediate: false, immediateCallback: true },
  );

  onMounted(() => {
    resume();
  });

  onUnmounted(() => {
    pause();
  });

  const total = computed(() => latestMessageOverview.value?.totalFiltered ?? 0);
  const messages = computed(() => {
    const overview = latestMessageOverview.value;
    if (!overview) return [];

    const reversed = reversedMessageQueue.value ?? false;

    const messages: Message[] = new Array(overview.totalFiltered);
    for (let i = 0; i < overview.totalFiltered; i++) {
      const msg = latestMessage.value?.messages?.find((msg) => msg.offset === i);
      if (msg) {
        messages[i] = {
          type: "loaded",
          htmlContent: msg.content,
          index: i,
          uuid: overview.messages[i].uuid,
          sequenceNumber: overview.messages[i].sequenceNumber,
        };
      } else {
        messages[i] = {
          type: "loading",
          index: i,
          uuid: overview.messages[i].uuid,
          sequenceNumber: overview.messages[i].sequenceNumber,
        };
      }
    }
    return reversed ? messages.toReversed() : messages;
  });

  let messageFetchParams: {
    fromOffset: number;
    toOffsetExcluding: number;
    filterRbelPath?: string;
  } = { fromOffset: -1, toOffsetExcluding: -1, filterRbelPath: "" };
  let messageFetchAbortController = new AbortController();
  const update = async (orderedStartIndex: number, orderedEndIndex: number) => {
    // prevent an endless loading loop if we're already inside the current view

    const reversed = reversedMessageQueue.value ?? false;

    const messageLength = messages.value.length;
    const actualStartIndex = messages.value[Math.min(messageLength - 1, orderedStartIndex)].index;
    const actualEndIndex = messages.value[Math.min(messageLength - 1, orderedEndIndex)].index;

    // reverse the index to match original backend order
    const startIndex = reversed ? actualEndIndex : actualStartIndex;
    const endIndex = reversed ? actualStartIndex : actualEndIndex;

    const isSame =
      messageFetchParams?.fromOffset === startIndex &&
      messageFetchParams?.toOffsetExcluding === endIndex + 1 &&
      messageFetchParams?.filterRbelPath === filterRbelPath.value;

    if (latestMessage.value == null || !isSame) {
      try {
        if (!messageFetchAbortController.signal.aborted) messageFetchAbortController.abort();
        messageFetchAbortController = new AbortController();
        messageFetchParams = {
          fromOffset: startIndex,
          toOffsetExcluding: endIndex + 1,
          filterRbelPath: filterRbelPath.value,
        };
        const result = await proxyController.getMessages(
          {
            ...messageFetchParams,
            signal: messageFetchAbortController.signal,
          },
          { suppressError: true, propagateError: true },
        );
        if (result) latestMessage.value = result;
      } catch {
        // noop
      }
    }
  };

  const internalDebounceScrollToMessage = useDebounceFn(
    (uuid: string) => {
      // get the actual index from the entire list
      const index = messages.value.findIndex((msg) => msg.uuid === uuid);
      dynamicScrollerRef.value?.scrollToItem(index);
      nextTick(async () => {
        // FIXME: Sometimes the endless scroller won't scroll to the correct position,
        //  so we try here again. This is a workaround and should be fixed in the future.
        await new Promise((resolve) => setTimeout(resolve, 100));
        dynamicScrollerRef.value?.scrollToItem(index);
      });
    },
    150,
    { rejectOnCancel: true },
  );

  const scrollToMessage = (uuid: string) => {
    internalDebounceScrollToMessage(uuid);
  };

  const reset = () => {
    latestMessageOverview.value = null;
  };

  return {
    messagesMeta,
    total,
    reversedMessageQueue: readonly(reversedMessageQueue),
    scrollToMessage,
    reset,
    internal: {
      update,
      messages,
      ref: dynamicScrollerRef,
    },
  };
}
