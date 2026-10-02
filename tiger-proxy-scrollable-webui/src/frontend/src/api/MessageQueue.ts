///
///
/// Copyright 2021-2025 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
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
import { computedWithControl, useDebounceFn } from "@vueuse/core";
import { useProxyController, type UseProxyControllerOptions } from "@/api/ProxyController.ts";
import type { MessageSortOrder } from "@/Settings.ts";
import { nextPaint, runSelfClockingLoop, whenIdle } from "@/api/scheduling.ts";

/**
 * One row of the message list.
 *
 * It deliberately carries no rendered HTML. The rows are the virtual scroller's `items`,
 * and the scroller walks that entire array - several times, one of them synchronously -
 * whenever the array changes. Keeping the HTML out means a chunk of rendered messages
 * arriving while the user scrolls is no longer "the list changed", so the walk happens
 * only when the queue itself moves. The HTML is looked up per rendered row instead, via
 * `internal.getRenderedHtml`.
 */
export type Message = {
  index: number;
  uuid: string;
  sequenceNumber: number;
};

export type MessageUiState = {
  details?: boolean;
  headers?: boolean;
  body?: boolean;
  sections?: Record<string, boolean>;
};

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
  scrollToMessage: (uuid: string, sequenceNumber?: number) => void;
  /**
   * Delete all fetched messages.
   */
  reset: () => void;
  /**
   * Internal properties to be set.
   */
  internal: {
    update: (orderedStartIndex: number, orderedEndIndex: number) => Promise<void>;
    messages: ComputedRef<Message[]>;
    /**
     * Rendered HTML of a message, if the currently loaded chunk holds it. Called per
     * rendered row rather than being carried in the rows themselves.
     */
    getRenderedHtml: (uuid: string) => string | undefined;
    ref: Ref<any | null>;
    getUiState: (uuid: string) => MessageUiState;
    setUiState: (uuid: string, state: Partial<MessageUiState>) => void;
  };
}

export interface UseMessageQueueOptions extends UseProxyControllerOptions {}

export const messageQueueSymbol: InjectionKey<UseMessageQueueReturn> = Symbol("messageQueueSymbol");

export interface TimestampRange {
  /** ISO-8601 instant strings (inclusive), e.g. `date.toISOString()`. */
  min?: string;
  max?: string;
}

/**
 * Lower bound between two polls. It is a floor rather than a period: a cycle that takes
 * longer than this simply starts the next one late instead of queueing up behind it.
 */
const MIN_POLL_INTERVAL_MS = 1000;

/**
 * How long we are willing to wait for an idle main thread before polling anyway. Without
 * the cap a permanently busy tab would stop updating altogether instead of just updating
 * less often.
 */
const MAX_MAIN_THREAD_WAIT_MS = 2000;

export function useMessageQueue(
  reversedMessageQueue: Ref<boolean>,
  rbelFilter: Ref<string>,
  messageSortOrder: Ref<MessageSortOrder>,
  options: UseMessageQueueOptions,
  timestampRange?: Ref<TimestampRange | undefined>,
): UseMessageQueueReturn {
  const proxyController = useProxyController(options);

  /** Aborted when the component goes away; ends the poll loop and any pending flush. */
  const lifetime = new AbortController();

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

  const minTimestamp = computed(() => timestampRange?.value?.min);
  const maxTimestamp = computed(() => timestampRange?.value?.max);

  async function loadMessageOverview() {
    const oldResult = latestMessageOverview.value;
    const newResult = await proxyController.getMetaMessages({
      filterRbelPath: filterRbelPath.value,
      minTimestamp: minTimestamp.value,
      maxTimestamp: maxTimestamp.value,
      sortOrder: messageSortOrder.value,
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

  watch([minTimestamp, maxTimestamp], async ([newMin, newMax], [oldMin, oldMax]) => {
    if (newMin !== oldMin || newMax !== oldMax) {
      latestMessage.value = null;
      latestMessageOverview.value = null;
      await loadMessageOverview();
    }
  });

  // When the user toggles the sort key, drop cached chunks and force a refetch so
  // that the displayed order matches the new selection immediately.
  watch(messageSortOrder, async (newOrder, oldOrder) => {
    if (newOrder !== oldOrder) {
      latestMessage.value = null;
      latestMessageOverview.value = null;
      await loadMessageOverview();
    }
  });

  /**
   * Re-fetch the overview only once the backend tells us the queue actually moved.
   *
   * The overview carries one entry per message, so polling it outright costs bandwidth and
   * main-thread time proportional to the size of the log - every second, for a log that is
   * usually unchanged. The status call is constant-size instead, which is what keeps large
   * logs usable while tracing is live. The status endpoint ignores the scoping/filter
   * params by design (it short-circuits the backend stream before the filter is evaluated),
   * so it only signals "the underlying queue moved" - loadMessageOverview() still applies
   * the current filterRbelPath/min/maxTimestamp when it re-fetches.
   */
  async function refreshOverviewIfQueueMoved() {
    const current = latestMessageOverview.value;
    if (!current) {
      await loadMessageOverview();
      return;
    }
    const status = await proxyController.getMessageQueueStatus({});
    if (!status) return;
    if (status.hash !== current.hash || status.total !== current.total) {
      await loadMessageOverview();
    }
  }

  /**
   * Wait until the refresh we just did is actually on screen and the browser has time to
   * spare again: Vue applies the update, two frames bracket the paint that draws it, and
   * the idle callback tells us the main thread is free. Polling behind this is what keeps
   * the pointer responsive on a busy log - the rendering back-pressures the poll instead
   * of the poll starving the rendering.
   */
  async function settleRendering(signal: AbortSignal) {
    await nextTick();
    await nextPaint(signal);
    await nextPaint(signal);
    await whenIdle(MAX_MAIN_THREAD_WAIT_MS, signal);
  }

  onMounted(() => {
    void runSelfClockingLoop({
      signal: lifetime.signal,
      poll: refreshOverviewIfQueueMoved,
      minIntervalMs: MIN_POLL_INTERVAL_MS,
      settle: settleRendering,
    });
  });

  onUnmounted(() => {
    lifetime.abort();
  });

  const total = computed(() => latestMessageOverview.value?.totalFiltered ?? 0);
  const uiStateMap: Map<string, MessageUiState> = new Map();

  function getUiState(uuid: string): MessageUiState {
    let s = uiStateMap.get(uuid);
    if (!s) {
      s = { details: true, headers: true, body: true, sections: {} };
      uiStateMap.set(uuid, s);
    }
    return s;
  }

  function setUiState(uuid: string, newState: Partial<MessageUiState>) {
    const s = getUiState(uuid);
    Object.assign(s, newState);
  }

  /**
   * Rendered HTML of the currently loaded chunk, keyed by uuid.
   *
   * A lookup, not part of the rows: reading it per rendered row keeps a chunk arriving
   * out of the scroller's `items`, and it replaces the linear scan that used to run for
   * every one of the (up to tens of thousands of) entries of the overview.
   */
  const renderedHtmlByUuid = computed(() => {
    const byUuid = new Map<string, string>();
    for (const loaded of latestMessage.value?.messages ?? []) {
      byUuid.set(loaded.uuid, loaded.content);
    }
    return byUuid;
  });

  function getRenderedHtml(uuid: string): string | undefined {
    return renderedHtmlByUuid.value.get(uuid);
  }

  /**
   * Rows are memoised twice over, because both identities matter to the virtual scroller.
   *
   * Per row: every rendered row lists its own item in `size-dependencies`, so a fresh
   * object means "this row changed" and costs a re-measure and a layout.
   *
   * Per array: a fresh array means "the list changed" and costs the scroller a full walk
   * of it - key extraction, a size accumulator and a synchronous watcher, all O(number of
   * messages). That is worth avoiding, because the backend's history revision (which is
   * what tells us to re-read the overview) bumps on every metadata change of every
   * message - thousands of times more often than the message list actually grows.
   */
  let messageRowsByUuid: Map<string, Message> = new Map();
  let messageRows: Message[] = [];
  let reversedMessages: Message[] = [];
  let reversedFrom: Message[] | null = null;

  const messages = computed(() => {
    const overview = latestMessageOverview.value;
    if (!overview) {
      messageRowsByUuid = new Map();
      messageRows = [];
      reversedFrom = null;
      return messageRows;
    }

    const nextRowsByUuid = new Map<string, Message>();
    const nextRows: Message[] = new Array(overview.totalFiltered);
    let allRowsReused = messageRows.length === overview.totalFiltered;
    for (let i = 0; i < overview.totalFiltered; i++) {
      const meta = overview.messages[i];
      const previous = messageRowsByUuid.get(meta.uuid);
      const row =
        previous !== undefined &&
        previous.index === i &&
        previous.sequenceNumber === meta.sequenceNumber
          ? previous
          : { index: i, uuid: meta.uuid, sequenceNumber: meta.sequenceNumber };
      if (row !== previous) allRowsReused = false;
      nextRowsByUuid.set(meta.uuid, row);
      nextRows[i] = row;
    }
    // Rebuilt from scratch so that messages the proxy has dropped do not linger.
    messageRowsByUuid = nextRowsByUuid;
    if (!allRowsReused) messageRows = nextRows;

    if (!(reversedMessageQueue.value ?? false)) return messageRows;
    if (reversedFrom !== messageRows) {
      reversedFrom = messageRows;
      reversedMessages = messageRows.toReversed();
    }
    return reversedMessages;
  });

  let messageFetchParams: {
    fromOffset: number;
    toOffsetExcluding: number;
    filterRbelPath?: string;
    minTimestamp?: string;
    maxTimestamp?: string;
    sortOrder?: MessageSortOrder;
    hash?: string;
  } = {
    fromOffset: -1,
    toOffsetExcluding: -1,
    filterRbelPath: "",
    sortOrder: undefined,
    hash: "",
  };
  let messageFetchAbortController = new AbortController();
  const fetchMessagesForRange = async (orderedStartIndex: number, orderedEndIndex: number) => {
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
      messageFetchParams?.filterRbelPath === filterRbelPath.value &&
      messageFetchParams?.minTimestamp === minTimestamp.value &&
      messageFetchParams?.maxTimestamp === maxTimestamp.value &&
      messageFetchParams?.sortOrder === messageSortOrder.value &&
      messageFetchParams?.hash === latestMessageOverview.value?.hash;

    if (latestMessage.value == null || !isSame) {
      try {
        if (!messageFetchAbortController.signal.aborted) messageFetchAbortController.abort();
        messageFetchAbortController = new AbortController();
        messageFetchParams = {
          fromOffset: startIndex,
          toOffsetExcluding: endIndex + 1,
          filterRbelPath: filterRbelPath.value,
          minTimestamp: minTimestamp.value,
          maxTimestamp: maxTimestamp.value,
          sortOrder: messageSortOrder.value,
          hash: latestMessageOverview.value?.hash,
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

  /**
   * The virtual scroller emits `update` from inside its own scroll and layout handling, so
   * all we do there is record the range it asks for. Reading the message list and starting
   * a chunk fetch from that callback re-entered the scroller's own update pass - fetch,
   * new items, re-measure, another update, another fetch - and it meant a fast scroll fired
   * dozens of requests per second only to abort nearly all of them. The recorded range is
   * picked up once the frame has been painted, coalesced into a single fetch.
   */
  let requestedRange: { orderedStartIndex: number; orderedEndIndex: number } | null = null;
  let rangeFlushScheduled = false;

  const update = async (orderedStartIndex: number, orderedEndIndex: number) => {
    requestedRange = { orderedStartIndex, orderedEndIndex };
    if (rangeFlushScheduled || lifetime.signal.aborted) return;
    rangeFlushScheduled = true;
    await nextPaint(lifetime.signal);
    rangeFlushScheduled = false;
    const range = requestedRange;
    requestedRange = null;
    if (range && !lifetime.signal.aborted) {
      await fetchMessagesForRange(range.orderedStartIndex, range.orderedEndIndex);
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

  const internalDebounceScrollToMessageBySequenceNumber = useDebounceFn(
    (sequenceNumber: number) => {
      // get the actual index from the entire list
      const index = messages.value.findIndex((msg) => msg.sequenceNumber === sequenceNumber);
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

  const scrollToMessage = (uuid: string, sequenceNumber?: number) => {
    if (uuid === "" && sequenceNumber) {
      internalDebounceScrollToMessageBySequenceNumber(sequenceNumber);
      return;
    }
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
      getRenderedHtml,
      ref: dynamicScrollerRef,
      getUiState,
      setUiState,
    },
  };
}
