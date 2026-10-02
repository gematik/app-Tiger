///
///
/// Copyright 2021-2026 gematik GmbH
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

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, ref, type Ref } from "vue";
import { type Message, useMessageQueue, type UseMessageQueueReturn } from "../src/api/MessageQueue";
import type { GetAllMessagesDto, MetaMessageDto } from "../src/api/MessageTypes";
import type { MessageSortOrder } from "../src/Settings";

// Every row of the virtual scroller lists its own item in `size-dependencies`, so handing
// out a fresh object for a message that did not change makes that row re-measure and the
// browser lay it out again. Rebuilding the whole list on each refresh therefore re-entered
// layout for the entire visible set once a second, which is what made the pointer stutter
// on a large log - regardless of how many bytes the refresh cost. These tests pin that
// unchanged messages keep their object identity across a refresh.

const mockFetch = vi.fn();

function metaMessage(sequenceNumber: number): MetaMessageDto {
  return {
    uuid: `uuid-${sequenceNumber}`,
    offset: sequenceNumber,
    sequenceNumber,
    infoString: `GET /message/${sequenceNumber}`,
    additionalInfoStrings: [],
    timestamp: "2026-09-24T10:00:00Z",
    request: sequenceNumber % 2 === 0,
    pairedSequenceNumbers: [],
    recipient: "server",
    sender: "client",
  };
}

/** The queue as the proxy currently reports it; mutated by the tests between refreshes. */
let currentOverview: GetAllMessagesDto;

function setQueue(sequenceNumbers: number[], hash: string) {
  const messages = sequenceNumbers.map(metaMessage);
  messages.forEach((message, index) => (message.offset = index));
  currentOverview = {
    total: messages.length,
    totalFiltered: messages.length,
    hash,
    filter: null,
    messages,
  };
}

function jsonResponse(body: unknown) {
  return {
    ok: true,
    headers: new Headers({ "Content-Type": "application/json" }),
    json: async () => body,
  };
}

/** Requests for rendered message HTML, i.e. everything but the constant-size status poll. */
let chunkRequests: string[] = [];

beforeEach(() => {
  setQueue([], "empty");
  chunkRequests = [];
  mockFetch.mockReset();
  mockFetch.mockImplementation((url: string) => {
    if (url.includes("getMessagesWithMeta")) {
      return Promise.resolve(jsonResponse(currentOverview));
    }
    if (url.includes("getMessagesWithHtml")) {
      if (url.includes("toOffsetExcluding=0")) {
        // The status poll: constant size, and it must not move the queue on its own.
        return Promise.resolve(
          jsonResponse({ total: currentOverview.total, hash: currentOverview.hash, messages: [] }),
        );
      }
      chunkRequests.push(url);
      const fromOffset = Number(new URL(url, "http://proxy").searchParams.get("fromOffset"));
      const toOffsetExcluding = Number(
        new URL(url, "http://proxy").searchParams.get("toOffsetExcluding"),
      );
      const slice = currentOverview.messages.slice(fromOffset, toOffsetExcluding);
      return Promise.resolve(
        jsonResponse({
          total: currentOverview.total,
          totalFiltered: slice.length,
          hash: currentOverview.hash,
          filter: null,
          fromOffset,
          toOffsetExcluding,
          messages: slice.map((meta) => ({
            uuid: meta.uuid,
            offset: meta.offset,
            sequenceNumber: meta.sequenceNumber,
            content: `<div>${meta.uuid}</div>`,
          })),
        }),
      );
    }
    return Promise.resolve(jsonResponse({}));
  });
  (globalThis as any).fetch = mockFetch;
});

type MountedQueue = {
  queue: UseMessageQueueReturn;
  filter: Ref<string>;
  unmount: () => void;
};

function mountQueue(): MountedQueue {
  const reversed = ref(false);
  const filter = ref("");
  const sortOrder = ref<MessageSortOrder>("TIMESTAMP");
  let queue: UseMessageQueueReturn | null = null;

  const wrapper = mount(
    defineComponent({
      setup() {
        queue = useMessageQueue(reversed, filter, sortOrder, {});
        return () => h("div");
      },
    }),
  );

  expect(queue).not.toBeNull();
  return { queue: queue!, filter, unmount: () => wrapper.unmount() };
}

let mounted: MountedQueue | null = null;

afterEach(() => {
  mounted?.unmount();
  mounted = null;
});

/**
 * Force a refresh of the overview without waiting out the poll interval: changing the
 * filter re-reads it through exactly the same code path the poll uses.
 */
async function refreshOverview(queue: MountedQueue, filterValue: string) {
  queue.filter.value = filterValue;
  await flushPromises();
  await flushPromises();
}

function uuidsOf(messages: readonly Message[]) {
  return messages.map((message) => message.uuid);
}

describe("message list item identity", () => {
  it("keeps the object of a message that did not change when the queue grows", async () => {
    mounted = mountQueue();
    setQueue([1, 2, 3], "h1");
    await refreshOverview(mounted, "isRequest");

    const before = [...mounted.queue.internal.messages.value];
    expect(uuidsOf(before)).toEqual(["uuid-1", "uuid-2", "uuid-3"]);

    setQueue([1, 2, 3, 4, 5], "h2");
    await refreshOverview(mounted, "isResponse");

    const after = mounted.queue.internal.messages.value;
    expect(uuidsOf(after)).toEqual(["uuid-1", "uuid-2", "uuid-3", "uuid-4", "uuid-5"]);
    expect(after[0]).toBe(before[0]);
    expect(after[1]).toBe(before[1]);
    expect(after[2]).toBe(before[2]);
    expect(after[3]).not.toBe(before[2]);
  });

  it("hands out a new object when a message shifts position", async () => {
    mounted = mountQueue();
    setQueue([1, 2, 3], "h1");
    await refreshOverview(mounted, "isRequest");

    const before = [...mounted.queue.internal.messages.value];

    // The proxy dropped the oldest message from its buffer, so every remaining message
    // moved up by one and has to be re-measured at its new position.
    setQueue([2, 3], "h2");
    await refreshOverview(mounted, "isResponse");

    const after = mounted.queue.internal.messages.value;
    expect(uuidsOf(after)).toEqual(["uuid-2", "uuid-3"]);
    expect(after[0]).not.toBe(before[1]);
    expect(after[0].index).toBe(0);
  });

  it("does not touch the rows at all when rendered content arrives", async () => {
    mounted = mountQueue();
    setQueue([1, 2, 3, 4], "h1");
    await refreshOverview(mounted, "isRequest");

    const before = mounted.queue.internal.messages.value;
    expect(before.map((row) => mounted!.queue.internal.getRenderedHtml(row.uuid))).toEqual([
      undefined,
      undefined,
      undefined,
      undefined,
    ]);

    mounted.queue.internal.update(0, 1, 0, 1);
    await vi.waitFor(() => expect(chunkRequests.length).toBe(1));
    await flushPromises();

    // The HTML is looked up per rendered row, so it arriving is not "the list changed":
    // the rows and the array holding them are the very same objects, and the scroller has
    // no reason to walk the list or re-measure anything but the rows that got content.
    const after = mounted.queue.internal.messages.value;
    expect(after).toBe(before);
    expect(after[0]).toBe(before[0]);
    expect(mounted.queue.internal.getRenderedHtml(after[0].uuid)).toBe("<div>uuid-1</div>");
    expect(mounted.queue.internal.getRenderedHtml(after[1].uuid)).toBe("<div>uuid-2</div>");
    expect(mounted.queue.internal.getRenderedHtml(after[2].uuid)).toBeUndefined();
  });

  it("hands back the identical array when the queue itself did not move", async () => {
    mounted = mountQueue();
    setQueue([1, 2, 3], "h1");
    await refreshOverview(mounted, "isRequest");

    const before = mounted.queue.internal.messages.value;

    // Same messages, new history revision - which is the common case, because the backend
    // bumps the revision on every metadata change of every message. The scroller walks its
    // whole `items` array whenever that array is a new one, so it has to stay the same one.
    setQueue([1, 2, 3], "h2");
    await refreshOverview(mounted, "isResponse");

    expect(mounted.queue.internal.messages.value).toBe(before);
  });

  it("coalesces the ranges the scroller reports into a single chunk request", async () => {
    mounted = mountQueue();
    setQueue([1, 2, 3, 4, 5, 6], "h1");
    await refreshOverview(mounted, "isRequest");

    // A fast scroll emits `update` many times per frame; it used to start (and abort) a
    // request for each of them.
    mounted.queue.internal.update(0, 1, 0, 1);
    mounted.queue.internal.update(1, 2, 1, 2);
    mounted.queue.internal.update(2, 3, 2, 3);

    await vi.waitFor(() => expect(chunkRequests.length).toBe(1));
    await flushPromises();

    expect(chunkRequests[0]).toContain("fromOffset=2");
    expect(chunkRequests[0]).toContain("toOffsetExcluding=4");
  });
});
