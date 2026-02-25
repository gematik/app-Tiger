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

import { ref, watch } from "vue";

const DEFAULT_FAVICON = "/img/tiger-mono-64.png";

const hasNotification = ref(false);
let notificationFaviconDataUrl: string | null = null;
let originalFaviconLoaded = false;

/**
 * Creates a notification badge favicon dynamically using Canvas.
 * Draws a red notification dot on top of the original favicon.
 */
async function createNotificationFavicon(): Promise<string> {
  return new Promise((resolve, reject) => {
    const canvas = document.createElement("canvas");
    canvas.width = 64;
    canvas.height = 64;
    const ctx = canvas.getContext("2d");

    if (!ctx) {
      reject(new Error("Canvas context not available"));
      return;
    }

    const img = new Image();
    img.crossOrigin = "anonymous";

    img.onload = () => {
      // Draw original favicon
      ctx.drawImage(img, 0, 0, 64, 64);

      // Draw red notification badge circle
      ctx.fillStyle = "#dc3545"; // Bootstrap danger red
      ctx.beginPath();
      ctx.arc(50, 14, 12, 0, 2 * Math.PI);
      ctx.fill();

      // White border for better visibility
      ctx.strokeStyle = "#ffffff";
      ctx.lineWidth = 2;
      ctx.stroke();

      // Convert to data URL
      const dataUrl = canvas.toDataURL("image/png");
      resolve(dataUrl);
    };

    img.onerror = () => {
      reject(new Error("Failed to load original favicon"));
    };

    img.src = DEFAULT_FAVICON;
  });
}

export function useFavicon() {
  /**
   * Initialize the notification favicon (preload)
   */
  async function initNotificationFavicon() {
    if (!originalFaviconLoaded) {
      try {
        notificationFaviconDataUrl = await createNotificationFavicon();
        originalFaviconLoaded = true;
      } catch (error) {
        console.error("Failed to create notification favicon:", error);
      }
    }
  }

  /**
   * Update the favicon based on notification state
   */
  function updateFavicon() {
    const link = document.querySelector(
      "link[rel~='icon']",
    ) as HTMLLinkElement | null;
    if (link) {
      if (hasNotification.value && notificationFaviconDataUrl) {
        link.href = notificationFaviconDataUrl;
      } else {
        link.href = DEFAULT_FAVICON;
      }
    }
  }

  /**
   * Set notification state and update favicon
   */
  async function setNotification(state: boolean) {
    // Ensure notification favicon is ready
    if (state && !notificationFaviconDataUrl) {
      await initNotificationFavicon();
    }

    hasNotification.value = state;
    updateFavicon();
  }

  /**
   * Reset to default favicon
   */
  function resetFavicon() {
    setNotification(false);
  }

  // Watch for changes in notification state
  watch(hasNotification, () => {
    updateFavicon();
  });

  // Initialize on first use
  initNotificationFavicon();

  return {
    hasNotification,
    setNotification,
    resetFavicon,
    updateFavicon,
  };
}
