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

// We need to mock fetch and verify the URL being constructed by ProxyRepository.

const mockFetch = vi.fn();
(globalThis as any).fetch = mockFetch;

beforeEach(() => {
  mockFetch.mockReset();
  mockFetch.mockResolvedValue({
    ok: true,
    headers: new Headers({ "Content-Type": "application/json" }),
    json: async () => ({ total: 0, totalFiltered: 0, hash: "x", messages: [] }),
  });
});

// Force online-mode repository
async function importRepo() {
  // Reset module cache so the conditional `getProxy()` selection is re-evaluated.
  vi.resetModules();
  return await import("../src/api/ProxyRepository");
}

describe("ProxyRepository sort order forwarding", () => {
  it("includes sortOrder=TIMESTAMP in /getMessagesWithMeta query", async () => {
    const { getProxy } = await importRepo();
    const repo = getProxy();

    await repo.fetchMessagesWithMeta({ sortOrder: "TIMESTAMP" });

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toContain("/webui/getMessagesWithMeta");
    expect(url).toContain("sortOrder=TIMESTAMP");
  });

  it("includes sortOrder=SEQUENCE in /getMessagesWithHtml query", async () => {
    const { getProxy } = await importRepo();
    const repo = getProxy();

    await repo.fetchMessagesWithHtml({
      fromOffset: 0,
      toOffsetExcluding: 10,
      sortOrder: "SEQUENCE",
      signal: new AbortController().signal,
    });

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toContain("/webui/getMessagesWithHtml");
    expect(url).toContain("fromOffset=0");
    expect(url).toContain("toOffsetExcluding=10");
    expect(url).toContain("sortOrder=SEQUENCE");
  });

  it("omits sortOrder from URL if not provided (server applies default)", async () => {
    const { getProxy } = await importRepo();
    const repo = getProxy();

    await repo.fetchMessagesWithMeta({});

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).not.toContain("sortOrder=");
  });

  it("forwards both filterRbelPath and sortOrder", async () => {
    const { getProxy } = await importRepo();
    const repo = getProxy();

    await repo.fetchMessagesWithMeta({
      filterRbelPath: "isRequest",
      sortOrder: "TIMESTAMP",
    });

    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toContain("filterRbelPath=isRequest");
    expect(url).toContain("sortOrder=TIMESTAMP");
  });
});


