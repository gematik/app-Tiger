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

/**
 * Waiting primitives and the self-clocking loop behind the message queue poll.
 *
 * The poll used to sit on a fixed 1 Hz timer that did not await its own callback: on a
 * busy proxy a refresh was started while the previous one was still fetching, parsing and
 * re-rendering, so the polls piled up and competed with the rendering for the main thread.
 * The loop here clocks itself instead - it only starts the next cycle once the previous
 * refresh has been applied, the browser has painted it and the main thread is free again.
 * The interval is therefore a lower bound, not a period: a log that the machine cannot
 * keep up with refreshes more slowly rather than freezing the pointer.
 *
 * Nothing in here depends on Vue, so the loop can be exercised without mounting anything.
 */

/**
 * Stand-in wait for environments without `requestIdleCallback` (jsdom, older Safari). The
 * frame waits before it already gave the browser its chance to paint, so a short delay is
 * enough to let queued work run.
 */
const IDLE_FALLBACK_MS = 16;

type IdleDeadline = { didTimeout: boolean; timeRemaining: () => number };

type IdleCapableGlobal = {
  requestIdleCallback?: (
    cb: (deadline: IdleDeadline) => void,
    opts?: { timeout: number },
  ) => number;
  cancelIdleCallback?: (handle: number) => void;
};

/** Resolves after `ms`, or right away once `signal` is aborted. */
export function delay(ms: number, signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.resolve();
  return new Promise<void>((resolve) => {
    const handle = setTimeout(finish, ms);

    function finish() {
      clearTimeout(handle);
      signal?.removeEventListener("abort", finish);
      resolve();
    }

    signal?.addEventListener("abort", finish, { once: true });
  });
}

/**
 * Resolves on the next animation frame. Two of these in a row bracket a paint: the first
 * callback still runs before the pending mutations are drawn, the second one after.
 */
export function nextPaint(signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.resolve();
  if (typeof requestAnimationFrame !== "function") return delay(IDLE_FALLBACK_MS, signal);
  return new Promise<void>((resolve) => {
    let handle: number | null = requestAnimationFrame(() => {
      handle = null;
      finish();
    });

    function finish() {
      if (handle !== null) cancelAnimationFrame(handle);
      signal?.removeEventListener("abort", finish);
      resolve();
    }

    signal?.addEventListener("abort", finish, { once: true });
  });
}

/**
 * Resolves once the browser reports the main thread as idle, at the latest after
 * `timeoutMs`. The cap matters: without it a permanently busy tab would stop polling
 * altogether instead of merely polling less often.
 */
export function whenIdle(timeoutMs: number, signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.resolve();
  const idleGlobal = globalThis as IdleCapableGlobal;
  const requestIdle = idleGlobal.requestIdleCallback;
  if (typeof requestIdle !== "function") return delay(IDLE_FALLBACK_MS, signal);
  return new Promise<void>((resolve) => {
    let handle: number | null = requestIdle.call(
      globalThis,
      () => {
        handle = null;
        finish();
      },
      { timeout: timeoutMs },
    );

    function finish() {
      if (handle !== null) idleGlobal.cancelIdleCallback?.call(globalThis, handle);
      signal?.removeEventListener("abort", finish);
      resolve();
    }

    signal?.addEventListener("abort", finish, { once: true });
  });
}

/**
 * Resolves as soon as the document is visible. A backgrounded tab gets no frames, so a
 * loop that waits for a paint would stall there anyway - but it would stall *after* having
 * fetched, which is exactly the traffic we do not want from a tab nobody is looking at.
 */
export function whenDocumentVisible(signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.resolve();
  if (typeof document === "undefined" || !document.hidden) return Promise.resolve();
  return new Promise<void>((resolve) => {
    function onVisibilityChange() {
      if (!document.hidden) finish();
    }

    function finish() {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      signal?.removeEventListener("abort", finish);
      resolve();
    }

    document.addEventListener("visibilitychange", onVisibilityChange);
    signal?.addEventListener("abort", finish, { once: true });
  });
}

export interface SelfClockingLoopOptions {
  /** Aborting this signal ends the loop at the next await. */
  signal: AbortSignal;
  /** The work of one cycle. Rejections are swallowed so a failed cycle cannot kill the loop. */
  poll: () => Promise<void>;
  /**
   * Lower bound between the *starts* of two cycles. A cycle that takes longer than this
   * does not add to it, so the loop never falls further behind than the machine does.
   */
  minIntervalMs: number;
  /**
   * Waits until the effects of the last cycle have been applied and the main thread is
   * free. Injected so the loop can be tested without a renderer.
   */
  settle: (signal: AbortSignal) => Promise<void>;
}

/**
 * Runs `poll` over and over, starting a cycle only when the previous one is fully done:
 * its fetch and parsing have finished, its DOM changes have been applied and painted, the
 * main thread is idle, at least `minIntervalMs` have passed and the tab is visible.
 */
export async function runSelfClockingLoop({
  signal,
  poll,
  minIntervalMs,
  settle,
}: SelfClockingLoopOptions): Promise<void> {
  while (!signal.aborted) {
    await whenDocumentVisible(signal);
    if (signal.aborted) return;

    const cycleStartedAt = Date.now();

    try {
      await poll();
    } catch {
      // A failed cycle must not end the loop; the next one tries again.
    }
    if (signal.aborted) return;

    await settle(signal);
    if (signal.aborted) return;

    await delay(Math.max(0, cycleStartedAt + minIntervalMs - Date.now()), signal);
  }
}
