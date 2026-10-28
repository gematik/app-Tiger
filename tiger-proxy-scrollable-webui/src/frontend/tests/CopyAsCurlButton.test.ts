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
import CopyAsCurlButton from "../src/components/CopyAsCurlButton.vue";

const { copy, parseHttpRequest, convertToCurl, show, hide, dispose, popoverShow, popoverDispose, popoverOptions } = vi.hoisted(() => ({
  copy: vi.fn(),
  parseHttpRequest: vi.fn(),
  convertToCurl: vi.fn(),
  show: vi.fn(),
  hide: vi.fn(),
  dispose: vi.fn(),
  popoverShow: vi.fn(),
  popoverDispose: vi.fn(),
  popoverOptions: vi.fn(),
}));

vi.mock("bootstrap", () => ({
  Tooltip: class {
    show = show;
    hide = hide;
    dispose = dispose;
  },
  Popover: class {
    constructor(_element: HTMLElement, options: object) {
      popoverOptions(options);
    }
    show = popoverShow;
    dispose = popoverDispose;
  },
}));

vi.mock("@vueuse/core", () => ({
  useClipboard: () => ({ copy }),
}));
vi.mock("../src/components/httpRequestParser", () => ({ parseHttpRequest }));
vi.mock("../src/components/httpRequestToCurl", () => ({ convertToCurl }));

describe("CopyAsCurlButton", () => {
  const request = { method: "GET", target: "/", headers: new Map(), body: "" };

  beforeEach(() => {
    vi.resetAllMocks();
    parseHttpRequest.mockReturnValue(request);
    convertToCurl.mockReturnValue("curl command");
    copy.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it.each(["cmd", "bash"])("copies the generated %s command", async (shell) => {
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    const item = wrapper.findAll(".dropdown-item").find((entry) => entry.text().includes(`(${shell})`))!;
    await item.trigger("click");
    await flushPromises();
    expect(parseHttpRequest).toHaveBeenCalledWith("raw request");
    expect(convertToCurl).toHaveBeenCalledWith(request, shell, "http");
    expect(copy).toHaveBeenCalledWith("curl command");
    expect(show).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-icon="check"]').exists()).toBe(true);
    expect(wrapper.find("output").text()).toBe("Copied!");
    wrapper.unmount();
  });

  it.each(["bash", "cmd"])("passes HTTPS metadata to the converter for %s", async (shell) => {
    const wrapper = mount(CopyAsCurlButton, {
      props: { rawMessageContent: "raw request", protocol: "https" },
    });
    const item = wrapper.findAll(".dropdown-item").find((entry) => entry.text().includes(`(${shell})`))!;
    await item.trigger("click");
    await flushPromises();
    expect(convertToCurl).toHaveBeenCalledWith(request, shell, "https");
    expect(copy).toHaveBeenCalledWith("curl command");
    wrapper.unmount();
  });

  it.each(["parser", "converter", "clipboard"])("logs a %s failure", async (stage) => {
    const error = new Error("Failed");
    const log = vi.spyOn(console, "error").mockImplementation(() => {});
    if (stage === "parser") {
      parseHttpRequest.mockImplementation(() => {
        throw error;
      });
    } else if (stage === "converter") {
      convertToCurl.mockImplementation(() => {
        throw error;
      });
    } else {
      copy.mockRejectedValue(error);
    }
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    await wrapper.findAll(".dropdown-item")[0].trigger("click");
    await flushPromises();
    expect(log).toHaveBeenCalledWith("Copy as cURL failed", error);
    expect(show).not.toHaveBeenCalled();
    expect(wrapper.find('[data-icon="check"]').exists()).toBe(false);
    expect(popoverShow).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-icon="triangle-exclamation"]').exists()).toBe(true);
    const expectedMessages: Record<string, string> = {
      parser: "This message could not be read as an HTTP request.",
      converter: "Failed",
      clipboard: "Clipboard access failed",
    };
    expect(wrapper.find('[role="alert"]').text()).toContain(expectedMessages[stage]);
    if (stage === "parser") {
      expect(wrapper.find('[role="alert"]').text()).not.toContain(error.message);
      expect(popoverOptions).toHaveBeenCalledWith(expect.objectContaining({
        content: expect.not.stringContaining(error.message),
      }));
    }
    if (stage !== "clipboard") {
      expect(copy).not.toHaveBeenCalled();
    }
    wrapper.unmount();
  });

  it("keeps errors visible until Escape or an outside click dismisses them", async () => {
    vi.useFakeTimers();
    copy.mockRejectedValue(new Error("Clipboard denied"));
    vi.spyOn(console, "error").mockImplementation(() => {});
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    await vi.advanceTimersByTimeAsync(5000);
    expect(wrapper.find('[role="alert"]').text()).toContain("Clipboard access failed");
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[role="alert"]').text()).toBe("");
    expect(popoverDispose).toHaveBeenCalledOnce();
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    document.body.click();
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[role="alert"]').text()).toBe("");
    wrapper.unmount();
  });

  it("replaces error feedback with success after a successful retry", async () => {
    copy.mockRejectedValueOnce(new Error("Clipboard denied"));
    vi.spyOn(console, "error").mockImplementation(() => {});
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    expect(popoverOptions).toHaveBeenCalledWith(expect.objectContaining({
      title: "Could not copy as cURL",
      html: false,
    }));
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    expect(popoverDispose).toHaveBeenCalledOnce();
    expect(wrapper.find('[role="alert"]').text()).toBe("");
    expect(wrapper.find("output").text()).toBe("Copied!");
    wrapper.unmount();
  });

  it("clears feedback after two seconds and restarts the timer on another copy", async () => {
    vi.useFakeTimers();
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1500);
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1500);
    expect(wrapper.find('[data-icon="check"]').exists()).toBe(true);
    expect(hide).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(500);
    expect(wrapper.find('[data-icon="check"]').exists()).toBe(false);
    expect(hide).toHaveBeenCalledOnce();
    expect(wrapper.find("output").text()).toBe("");
    wrapper.unmount();
  });

  it("disposes the tooltip and cancels pending feedback on unmount", async () => {
    vi.useFakeTimers();
    const wrapper = mount(CopyAsCurlButton, { props: { rawMessageContent: "raw request" } });
    await wrapper.find(".dropdown-item").trigger("click");
    await flushPromises();
    wrapper.unmount();
    expect(dispose).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(2000);
    expect(hide).not.toHaveBeenCalled();
  });
});
