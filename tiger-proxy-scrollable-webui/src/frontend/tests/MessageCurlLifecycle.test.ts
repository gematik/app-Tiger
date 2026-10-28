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

import { describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import Message from "../src/components/Message.vue";
import { messageQueueSymbol } from "../src/api/MessageQueue";
import { settingsSymbol } from "../src/Settings";
import { rawContentModalSymbol } from "../src/RawContentModal";
import { rbelQueryModalSymbol } from "../src/RbelQueryModal";

const { mounted, unmounted } = vi.hoisted(() => ({
  mounted: vi.fn(),
  unmounted: vi.fn(),
}));

vi.mock("../src/api/scheduling.ts", () => ({
  nextPaint: async () => {},
}));

vi.mock("../src/components/CopyAsCurlButton.vue", async () => {
  const { defineComponent, h, onMounted, onBeforeUnmount } = await import("vue");
  return {
    default: defineComponent({
      setup() {
        onMounted(mounted);
        onBeforeUnmount(unmounted);
        return () => h("button", { class: "curl-test-button" }, "cURL");
      },
    }),
  };
});

function html(label: string) {
  return `<div class="card-header">
    <div><button data-bs-target="#dialog-test">Raw</button></div>
    <div class="copy-as-curl-button-placeholder"></div>
    </div><div role="dialog"><pre>GET /${label} HTTP/1.1\r\nHost: example.org\r\n\r\n</pre></div>`;
}

describe("Message cURL component lifecycle", () => {
  it("unmounts the old button on HTML replacement and the current button on row removal", async () => {
    mounted.mockClear();
    unmounted.mockClear();
    const state = {};
    const wrapper = mount(Message, {
      props: {
        message: { index: 0, uuid: "request", sequenceNumber: 1 },
        htmlContent: html("first"),
        onToggleDetailsOrHeader: vi.fn(),
      },
      global: {
        provide: {
          [messageQueueSymbol]: { internal: { getUiState: () => state } },
          [settingsSymbol]: {
            hideMessageDetails: ref(false),
            hideMessageHeaders: ref(false),
          },
          [rawContentModalSymbol]: { show: vi.fn() },
          [rbelQueryModalSymbol]: { show: vi.fn() },
        },
      },
    });
    await flushPromises();
    expect(mounted).toHaveBeenCalledTimes(1);
    const previousButton = wrapper.get(".curl-test-button").element;

    await wrapper.setProps({ htmlContent: html("second") });
    await flushPromises();
    expect(unmounted).toHaveBeenCalledTimes(1);
    expect(mounted).toHaveBeenCalledTimes(2);
    expect(wrapper.get(".curl-test-button").element).not.toBe(previousButton);

    wrapper.unmount();
    expect(unmounted).toHaveBeenCalledTimes(2);
  });
});
