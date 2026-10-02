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

import { describe, expect, it, vi, beforeEach } from "vitest";

// The overview carries one entry per message, so the once-a-second poll must not fetch it
// unless the queue actually moved. Guards against regressing to polling
// /getMessagesWithMeta directly, which made large logs unusable (see ADR 011).

const mockFetch = vi.fn();
(globalThis as any).fetch = mockFetch;

function jsonResponse(body: unknown) {
  return {
    ok: true,
    headers: new Headers({ "Content-Type": "application/json" }),
    json: async () => body,
  };
}

beforeEach(() => {
  mockFetch.mockReset();
});

async function importRepo() {
  vi.resetModules();
  return await import("../src/api/ProxyRepository");
}

describe("message queue status poll", () => {
  it("asks for an empty offset range so the backend renders nothing", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ total: 5000, hash: "816486", messages: [] }));
    const { getProxy } = await importRepo();

    const status = await getProxy().fetchMessageQueueStatus({});

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toContain("/webui/getMessagesWithHtml");
    expect(url).toContain("fromOffset=0");
    expect(url).toContain("toOffsetExcluding=0");
    expect(status).toEqual({ total: 5000, hash: "816486" });
  });

  it("does not carry a filter, so the backend never evaluates one while polling", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ total: 1, hash: "a", messages: [] }));
    const { getProxy } = await importRepo();

    await getProxy().fetchMessageQueueStatus({});

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).not.toContain("filterRbelPath");
  });

  it("reports a moved queue through hash and total", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ total: 7, hash: "b", messages: [] }));
    const { getProxy } = await importRepo();

    const first = await getProxy().fetchMessageQueueStatus({});

    mockFetch.mockResolvedValue(jsonResponse({ total: 9, hash: "c", messages: [] }));
    const second = await getProxy().fetchMessageQueueStatus({});

    expect(first.hash).not.toEqual(second.hash);
    expect(first.total).not.toEqual(second.total);
  });
});
