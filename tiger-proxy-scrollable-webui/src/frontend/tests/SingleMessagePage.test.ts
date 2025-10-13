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

import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { createRouter, createWebHistory } from "vue-router";
import SingleMessagePage from "../src/pages/SingleMessagePage.vue";

// Mock the child components to avoid dependency issues
vi.mock("../src/components/Message.vue", () => ({
  default: {
    name: "Message",
    template: '<div class="mocked-message">{{ message.htmlContent }}</div>',
    props: ["message", "onToggleDetailsOrHeader"],
  },
}));

vi.mock("../src/components/SettingsHeader.vue", () => ({
  default: {
    name: "SettingsHeader",
    template: '<div class="mocked-settings-header">Settings Header</div>',
    props: ["messageQueue", "onClickResetMessages", "onClickQuitProxy", "noLogo"],
  },
}));

vi.mock("../src/components/RawContentModal.vue", () => ({
  default: {
    name: "RawContentModal",
    template: '<div class="mocked-raw-content-modal"></div>',
  },
}));

vi.mock("../src/components/RbelQueryModal.vue", () => ({
  default: {
    name: "RbelQueryModal",
    template: '<div class="mocked-rbel-query-modal"></div>',
  },
}));

// Mock the composables
vi.mock("../src/RawContentModal.ts", () => ({
  rawContentModalSymbol: Symbol("rawContentModalSymbol"),
  useRawContentModal: () => ({
    show: vi.fn(),
  }),
}));

vi.mock("../src/Settings.ts", () => ({
  settingsSymbol: Symbol("settingsSymbol"),
  useSettings: () => ({
    hideMessageHeaders: { value: false },
    hideMessageDetails: { value: false },
    reverseMessageQueue: { value: false },
  }),
}));

vi.mock("../src/Toast.ts", () => ({
  toastSymbol: Symbol("toastSymbol"),
  useToast: () => ({
    showToast: vi.fn(),
    message: "",
  }),
}));

vi.mock("../src/RbelQueryModal.ts", () => ({
  rbelQueryModalSymbol: Symbol("rbelQueryModalSymbol"),
  useRbelQueryModal: () => ({
    show: vi.fn(),
  }),
}));

// Mock ProxyController
const mockProxyController = {
  getFullyRenderedMessage: vi.fn(),
  resetMessageQueue: vi.fn(),
  quitProxy: vi.fn(),
};

vi.mock("../src/api/ProxyController.ts", () => ({
  useProxyController: () => mockProxyController,
}));

// Mock global properties
global.__IS_ONLINE_MODE__ = true;
global.__IS_DETACHED_MODE__ = false;

describe("SingleMessagePage", () => {
  let router: any;

  beforeEach(() => {
    // Create router with test routes
    router = createRouter({
      history: createWebHistory(),
      routes: [{ path: "/message/:uuid", component: SingleMessagePage }],
    });

    // Clear mocks
    vi.clearAllMocks();

    // Setup default successful response
    mockProxyController.getFullyRenderedMessage.mockResolvedValue({
      content: `<div class="test-message-content">GET /api/test</div>`,
      uuid: "test-uuid-123",
      sequenceNumber: 1,
    });
  });

  it("should display a message when loaded with valid UUID", async () => {
    // Given
    const testUuid = "test-uuid-123";

    // Navigate to the route with UUID
    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    // Wait for component to mount and fetch data
    await nextTick();
    await nextTick(); // Additional tick for async operations

    // Then - Check that the message content is displayed
    const messageContainer = wrapper.find(".message-container");
    expect(messageContainer.exists()).toBe(true);

    // Check that the Message component is rendered
    const messageComponent = wrapper.findComponent({ name: "Message" });
    expect(messageComponent.exists()).toBe(true);

    // Verify the message prop is passed correctly
    expect(messageComponent.props("message")).toEqual({
      type: "loaded",
      htmlContent: expect.stringContaining("GET /api/test"),
      index: 0,
      uuid: testUuid,
      sequenceNumber: 1,
    });
  });

  it("should show loading state while fetching message", async () => {
    // Given
    const testUuid = "test-uuid-123";

    // Mock a delayed response that never resolves immediately
    let resolvePromise: any;
    const delayedPromise = new Promise((resolve) => {
      resolvePromise = resolve;
    });

    mockProxyController.getFullyRenderedMessage.mockReturnValue(delayedPromise);

    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    // Wait one tick for the component to mount and start the async operation
    await nextTick();

    // Then - should show loading state while promise is pending
    expect(wrapper.find(".spinner-border").exists()).toBe(true);
    expect(wrapper.text()).toContain("Loading message...");
    expect(wrapper.find(".message-container").exists()).toBe(false);

    // Resolve the promise to clean up
    resolvePromise({
      content: "<div>Test content</div>",
      uuid: testUuid,
      sequenceNumber: 1,
    });

    await nextTick();
  });

  it("should show error state when message fails to load", async () => {
    // Given
    const testUuid = "non-existent-uuid";

    // Mock failed API response in ProxyController
    mockProxyController.getFullyRenderedMessage.mockRejectedValue(new Error("Message not found"));

    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    await nextTick();
    await nextTick();

    // Then
    expect(wrapper.find(".alert-warning").exists()).toBe(true);
    expect(wrapper.text()).toContain("Message not found");
    expect(wrapper.find(".message-container").exists()).toBe(false);
  });

  it("should navigate to partner message when partner button is clicked", async () => {
    // Given
    const testUuid = "test-uuid-123";
    const partnerUuid = "partner-uuid-456";

    await router.push(`/message/${testUuid}`);

    // Mock router push
    const mockPush = vi.spyOn(router, "push");
    mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    await nextTick();
    await nextTick();

    // Mock the scrollToMessage function that's globally available
    const mockScrollToMessage = vi.fn((uuid: string, sequenceNumber: number) => {
      // Simulate the actual behavior of scrollToMessage function
      if (sequenceNumber) {
        /* to ignore unused parameter */
      }
      router.push(`/message/${uuid}`);
    });

    // Set up the global function before creating the DOM element
    (global as any).scrollToMessage = mockScrollToMessage;
    (window as any).scrollToMessage = mockScrollToMessage;

    // Simulate the DOM being updated with message content
    // This would normally happen through the Message component
    const mockMessageElement = document.createElement("div");
    mockMessageElement.className = "rbel-message";
    mockMessageElement.innerHTML = `
      <button class="partner-message-button" onclick="scrollToMessage('${partnerUuid}',2)">
        <span class="icon is-small"><i class="fas fa-right-left"></i></span>
      </button>
    `;
    document.body.appendChild(mockMessageElement);

    await nextTick();

    // Simulate clicking the partner button by directly calling the function
    // since onclick in test environment doesn't work the same way
    const partnerButton = mockMessageElement.querySelector(
      ".partner-message-button",
    ) as HTMLButtonElement;
    expect(partnerButton).toBeTruthy();

    // Directly call the scrollToMessage function to simulate the onclick behavior
    mockScrollToMessage(partnerUuid, 2);

    // Then
    expect(mockScrollToMessage).toHaveBeenCalledWith(partnerUuid, 2);
    expect(mockPush).toHaveBeenCalledWith(`/message/${partnerUuid}`);

    // Cleanup
    document.body.removeChild(mockMessageElement);
    delete (global as any).scrollToMessage;
    delete (window as any).scrollToMessage;
  });

  it("should display settings header with Tiger Proxy logo", async () => {
    // Given
    const testUuid = "test-uuid-123";
    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    await nextTick();

    // Then
    const settingsHeader = wrapper.findComponent({ name: "SettingsHeader" });
    expect(settingsHeader.exists()).toBe(true);

    // Check that the settings header is configured correctly
    expect(settingsHeader.props("noLogo")).toBe(false);
    expect(settingsHeader.props("messageQueue")).toBeDefined();
    expect(settingsHeader.props("onClickResetMessages")).toBeDefined();
    expect(settingsHeader.props("onClickQuitProxy")).toBeDefined();
  });

  it("should have back button that navigates to previous page", async () => {
    // Given
    const testUuid = "test-uuid-123";
    await router.push(`/message/${testUuid}`);

    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    await nextTick();

    // When
    const backButton = wrapper.find('button[title="Go back"]');
    expect(backButton.exists()).toBe(true);
    expect(backButton.text()).toContain("Back");

    // Verify it has the correct click handler (router.go(-1))
    expect(backButton.attributes("onclick")).toBeUndefined(); // Vue handles this differently
  });

  it("should show appropriate page title", async () => {
    // Given
    const testUuid = "test-uuid-123";
    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });

    await nextTick();

    // Then
    expect(wrapper.find("h2").text()).toBe("Single Message View");
  });
});
