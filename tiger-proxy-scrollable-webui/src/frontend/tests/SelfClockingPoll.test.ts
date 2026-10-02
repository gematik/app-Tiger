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
import { delay, runSelfClockingLoop, whenDocumentVisible } from "../src/api/scheduling";

// The message queue poll used to sit on a fixed 1 Hz timer that did not await its own
// callback, so on a busy proxy the refreshes overlapped and starved the rendering. These
// tests pin the properties that replaced it: one cycle at a time, the next one only after
// the last one has been applied, the interval as a floor rather than a period, and no
// polling at all from a tab nobody is looking at.

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

function setDocumentHidden(hidden: boolean) {
  Object.defineProperty(document, "hidden", { configurable: true, get: () => hidden });
  document.dispatchEvent(new Event("visibilitychange"));
}

describe("self-clocking poll loop", () => {
  it("never runs two cycles at the same time", async () => {
    let inFlight = 0;
    let maxInFlight = 0;
    let cycles = 0;
    const abort = new AbortController();

    const loop = runSelfClockingLoop({
      signal: abort.signal,
      minIntervalMs: 0,
      settle: async () => {},
      poll: async () => {
        inFlight++;
        maxInFlight = Math.max(maxInFlight, inFlight);
        await delay(40);
        inFlight--;
        cycles++;
      },
    });

    await vi.advanceTimersByTimeAsync(500);
    abort.abort();
    await vi.advanceTimersByTimeAsync(100);
    await loop;

    expect(maxInFlight).toBe(1);
    expect(cycles).toBeGreaterThan(1);
  });

  it("waits for the rendering to settle before starting the next cycle", async () => {
    const order: string[] = [];
    const abort = new AbortController();

    const loop = runSelfClockingLoop({
      signal: abort.signal,
      minIntervalMs: 0,
      poll: async () => {
        order.push("poll");
      },
      settle: async () => {
        order.push("settle:start");
        await delay(20);
        order.push("settle:end");
        if (order.filter((step) => step === "poll").length >= 2) abort.abort();
      },
    });

    await vi.advanceTimersByTimeAsync(500);
    await loop;

    expect(order).toEqual([
      "poll",
      "settle:start",
      "settle:end",
      "poll",
      "settle:start",
      "settle:end",
    ]);
  });

  it("treats the interval as a floor measured from the start of the cycle", async () => {
    const startedAt: number[] = [];
    const abort = new AbortController();

    const loop = runSelfClockingLoop({
      signal: abort.signal,
      minIntervalMs: 60,
      settle: async () => {},
      poll: async () => {
        startedAt.push(Date.now());
        // A cycle that costs less than the floor must not push the next one out.
        await delay(50);
      },
    });

    await vi.advanceTimersByTimeAsync(200);
    abort.abort();
    await vi.advanceTimersByTimeAsync(200);
    await loop;

    const gaps = startedAt.slice(1).map((at, i) => at - startedAt[i]);
    expect(gaps.length).toBeGreaterThan(1);
    // 60 - the floor - and not 110, which is what waiting *after* the cycle would give.
    expect(gaps.every((gap) => gap === 60)).toBe(true);
  });

  it("keeps polling after a cycle failed", async () => {
    let cycles = 0;
    const abort = new AbortController();

    const loop = runSelfClockingLoop({
      signal: abort.signal,
      minIntervalMs: 10,
      settle: async () => {},
      poll: async () => {
        cycles++;
        throw new Error("proxy unreachable");
      },
    });

    await vi.advanceTimersByTimeAsync(100);
    abort.abort();
    await vi.advanceTimersByTimeAsync(50);
    await loop;

    expect(cycles).toBeGreaterThan(2);
  });

  it("does not poll while the tab is hidden and resumes when it is shown again", async () => {
    setDocumentHidden(true);
    let cycles = 0;
    const abort = new AbortController();

    const loop = runSelfClockingLoop({
      signal: abort.signal,
      minIntervalMs: 10,
      settle: async () => {},
      poll: async () => {
        cycles++;
      },
    });

    await vi.advanceTimersByTimeAsync(200);
    expect(cycles).toBe(0);

    setDocumentHidden(false);
    await vi.advanceTimersByTimeAsync(100);
    abort.abort();
    await vi.advanceTimersByTimeAsync(50);
    await loop;

    expect(cycles).toBeGreaterThan(0);
  });

  it("stops waiting for a hidden tab once the loop is aborted", async () => {
    setDocumentHidden(true);
    const abort = new AbortController();
    const waiting = whenDocumentVisible(abort.signal);

    abort.abort();
    await vi.advanceTimersByTimeAsync(0);

    await expect(waiting).resolves.toBeUndefined();
    setDocumentHidden(false);
  });
});
