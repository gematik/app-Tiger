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

/*
 * Copyright [2025], gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { useFavicon } from "../composables/useFavicon";

describe("useFavicon composable", () => {
  let mockLink: HTMLLinkElement;
  let originalCreateElement: typeof document.createElement;
  let originalImage: typeof Image;

  beforeEach(() => {
    // Store original implementations
    originalCreateElement = document.createElement.bind(document);
    originalImage = globalThis.Image;

    // Reset DOM and create mock favicon link element
    mockLink = originalCreateElement.call(document, "link");
    mockLink.rel = "icon";
    mockLink.href = "/img/tiger-mono-64.png";
    document.head.appendChild(mockLink);

    // Mock canvas context
    const mockContext = {
      drawImage: vi.fn(),
      fillStyle: "",
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      strokeStyle: "",
      lineWidth: 0,
      stroke: vi.fn(),
    };

    // Mock canvas element - use original for non-canvas elements
    document.createElement = ((tagName: string) => {
      if (tagName === "canvas") {
        return {
          width: 0,
          height: 0,
          getContext: () => mockContext,
          toDataURL: () => "data:image/png;base64,mockNotificationFavicon",
        } as unknown as HTMLCanvasElement;
      }
      return originalCreateElement.call(document, tagName);
    }) as typeof document.createElement;

    // Mock Image constructor
    globalThis.Image = class MockImage {
      crossOrigin = "";
      src = "";
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;

      constructor() {
        // Simulate successful image load after a short delay
        setTimeout(() => {
          if (this.onload) this.onload();
        }, 5);
      }
    } as unknown as typeof Image;
  });

  afterEach(() => {
    // Restore original implementations
    document.createElement = originalCreateElement;
    globalThis.Image = originalImage;

    // Clean up DOM
    if (mockLink.parentNode) {
      mockLink.parentNode.removeChild(mockLink);
    }

    vi.restoreAllMocks();
  });

  test("should return required functions", () => {
    const { setNotification, resetFavicon, updateFavicon, hasNotification } =
      useFavicon();

    expect(setNotification).toBeDefined();
    expect(resetFavicon).toBeDefined();
    expect(updateFavicon).toBeDefined();
    expect(hasNotification).toBeDefined();
  });

  test("hasNotification should be false initially", () => {
    const { hasNotification } = useFavicon();
    expect(hasNotification.value).toBe(false);
  });

  test("setNotification(true) should set hasNotification to true", async () => {
    const { setNotification, hasNotification } = useFavicon();

    await setNotification(true);

    expect(hasNotification.value).toBe(true);
  });

  test("setNotification(false) should set hasNotification to false", async () => {
    const { setNotification, hasNotification } = useFavicon();

    await setNotification(true);
    await setNotification(false);

    expect(hasNotification.value).toBe(false);
  });

  test("resetFavicon should set hasNotification to false", async () => {
    const { setNotification, resetFavicon, hasNotification } = useFavicon();

    await setNotification(true);
    expect(hasNotification.value).toBe(true);

    await resetFavicon();
    expect(hasNotification.value).toBe(false);
  });

  test("setNotification(true) should change favicon href to notification favicon", async () => {
    const { setNotification } = useFavicon();

    await setNotification(true);
    // Wait for async canvas generation
    await new Promise((resolve) => setTimeout(resolve, 20));

    const link = document.querySelector("link[rel~='icon']") as HTMLLinkElement;
    expect(link.href).toContain("mockNotificationFavicon");
  });

  test("setNotification(false) should reset favicon href to default", async () => {
    const { setNotification } = useFavicon();

    await setNotification(true);
    await new Promise((resolve) => setTimeout(resolve, 20));

    await setNotification(false);

    const link = document.querySelector("link[rel~='icon']") as HTMLLinkElement;
    expect(link.href).toContain("tiger-mono-64.png");
  });

  test("updateFavicon should update favicon based on current notification state", async () => {
    const { setNotification, updateFavicon } = useFavicon();

    // Set notification and wait for canvas generation
    await setNotification(true);
    await new Promise((resolve) => setTimeout(resolve, 20));

    // Manually reset href to test updateFavicon
    const link = document.querySelector("link[rel~='icon']") as HTMLLinkElement;
    link.href = "/some/other/path.png";

    // hasNotification is still true, so updateFavicon should restore notification favicon
    updateFavicon();

    expect(link.href).toContain("mockNotificationFavicon");
  });
});
