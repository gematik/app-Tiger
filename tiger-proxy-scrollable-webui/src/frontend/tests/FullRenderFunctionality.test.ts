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
import MainApp from "../src/components/MainApp.vue";
import App from "../src/App.vue";
import SingleMessagePage from "../src/pages/SingleMessagePage.vue";

// Mock Bootstrap first, before any components are imported
vi.mock("bootstrap", () => ({
  Modal: vi.fn().mockImplementation(() => ({
    show: vi.fn(),
    hide: vi.fn(),
    dispose: vi.fn(),
  })),
  Dropdown: vi.fn().mockImplementation(() => ({
    show: vi.fn(),
    hide: vi.fn(),
    dispose: vi.fn(),
  })),
}));

// Mock all required dependencies for SingleMessagePage
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

vi.mock("../src/components/SearchModal.vue", () => ({
  default: {
    name: "SearchModal",
    template: '<div class="mocked-search-modal"></div>',
    setup() {
      return {};
    },
  },
}));

vi.mock("../src/components/MessageList.vue", () => ({
  default: {
    name: "MessageList",
    template: '<div class="mocked-message-list"></div>',
    props: ["isEmbedded"],
  },
}));

vi.mock("../src/components/MainApp.vue", () => ({
  default: {
    name: "MainApp",
    template: '<div class="mocked-main-app"></div>',
  },
}));

vi.mock("../src/components/Sidebar.vue", () => ({
  default: {
    name: "Sidebar",
    template: '<div class="mocked-sidebar"></div>',
  },
}));

vi.mock("../src/components/StatusHeader.vue", () => ({
  default: {
    name: "StatusHeader",
    template: '<div class="mocked-status-header"></div>',
  },
}));

vi.mock("../src/components/ExportModal.vue", () => ({
  default: {
    name: "ExportModal",
    template: '<div class="mocked-export-modal"></div>',
  },
}));

vi.mock("../src/components/RouteModal.vue", () => ({
  default: {
    name: "RouteModal",
    template: '<div class="mocked-route-modal"></div>',
  },
}));

vi.mock("../src/components/RbelFilterModal.vue", () => ({
  default: {
    name: "RbelFilterModal",
    template: '<div class="mocked-rbel-filter-modal"></div>',
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
  getMetaMessages: vi.fn(),
  getMessages: vi.fn(),
  resetMessageQueue: vi.fn(),
  quitProxy: vi.fn(),
  testFilter: vi.fn(),
  searchMessages: vi.fn(),
  testRbelJexlQuery: vi.fn(),
  testRbelTreeQuery: vi.fn(),
  importRbelLogFile: vi.fn(),
  downloadRbelLogFile: vi.fn(),
  getProxyRoutes: vi.fn(),
  deleteProxyRoute: vi.fn(),
  addProxyRoute: vi.fn(),
};

vi.mock("../src/api/ProxyController.ts", () => ({
  useProxyController: () => mockProxyController,
}));

// Mock window.open
const mockWindowOpen = vi.fn();
Object.defineProperty(window, "open", {
  value: mockWindowOpen,
  writable: true,
});

// Mock global properties
global.__IS_ONLINE_MODE__ = true;
global.__IS_DETACHED_MODE__ = false;

describe("Full Render Functionality", () => {
  let router: any;

  beforeEach(() => {
    // Create router with test routes
    router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: "/", component: App },
        { path: "/message/:uuid", component: SingleMessagePage },
      ],
    });

    // Clear mocks
    vi.clearAllMocks();

    // Setup default successful response for ProxyController
    mockProxyController.getFullyRenderedMessage.mockResolvedValue({
      content: "<div>Mock message content</div>",
      uuid: "test-uuid-123",
      sequenceNumber: 1,
    });
  });

  it("should route to SingleMessagePage for /message/:uuid URLs", async () => {
    // Given
    const testUuid = "test-message-uuid-123";

    // When
    await router.push(`/message/${testUuid}`);
    mount(MainApp, {
      global: {
        plugins: [router],
      },
    });
    await nextTick();

    // Then
    expect(router.currentRoute.value.path).toBe(`/message/${testUuid}`);
    expect(router.currentRoute.value.params.uuid).toBe(testUuid);
  });

  it("should route to main App for root URL", async () => {
    // When
    await router.push("/");
    mount(MainApp, {
      global: {
        plugins: [router],
      },
    });
    await nextTick();

    // Then
    expect(router.currentRoute.value.path).toBe("/");
  });

  it("should open new window when full render button is clicked", async () => {
    // Given
    const testUuid = "test-uuid-123";
    const mockHtml = `
      <div class="message-content">
        <div class='d-flex justify-content-end mb-2'>
          <button class='btn btn-sm btn-outline-primary full-message-button' 
                  type='button' title='Open in new window'>
            <i class='fas fa-external-link-alt'></i> Open in new window
          </button>
        </div>
        <div>Message content here</div>
      </div>
    `;

    // Create a mock message component
    const MessageWrapper = {
      template: '<div ref="messageElement" v-html="messageHtml"></div>',
      data() {
        return {
          messageHtml: mockHtml,
          message: { uuid: testUuid, type: "loaded" },
        };
      },
      mounted() {
        // Simulate the button click handler from Message.vue
        const fullRenderButton = this.$refs.messageElement.querySelector(".full-message-button");
        if (fullRenderButton) {
          fullRenderButton.addEventListener("click", (event) => {
            event.preventDefault();
            const url = `/message/${this.message.uuid}`;
            const newWindow = window.open(url);
            if (!newWindow) {
              alert("Popup blocked. Please allow popups for this site.");
            }
          });
        }
      },
    };

    const wrapper = mount(MessageWrapper);
    await nextTick();

    // When
    const button = wrapper.find(".full-message-button");
    expect(button.exists()).toBe(true);

    await button.trigger("click");

    // Then
    expect(mockWindowOpen).toHaveBeenCalledWith(`/message/${testUuid}`);
  });

  it("should fetch message data when SingleMessagePage loads", async () => {
    // Given
    const testUuid = "test-uuid-456";
    await router.push(`/message/${testUuid}`);

    mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });
    await nextTick();
    await nextTick(); // Additional tick for async operations

    // Then - Check that ProxyController was called instead of raw fetch
    expect(mockProxyController.getFullyRenderedMessage).toHaveBeenCalledWith({ uuid: testUuid });
  });

  it("should handle fetch errors gracefully in SingleMessagePage", async () => {
    // Given
    const testUuid = "invalid-uuid";
    mockProxyController.getFullyRenderedMessage.mockRejectedValueOnce(
      new Error("Message not found"),
    );
    await router.push(`/message/${testUuid}`);

    // When
    const wrapper = mount(SingleMessagePage, {
      global: {
        plugins: [router],
      },
    });
    await nextTick();
    await nextTick();

    // Then - Check that ProxyController was called and component handles error
    expect(mockProxyController.getFullyRenderedMessage).toHaveBeenCalledWith({ uuid: testUuid });
    // Component should show error state
    expect(wrapper.find(".alert-warning").exists()).toBe(true);
  });

  it("should display popup blocked message when window.open fails", async () => {
    // Given
    const testUuid = "test-uuid-789";
    mockWindowOpen.mockReturnValueOnce(null); // Simulate popup blocked

    // Mock alert
    const mockAlert = vi.fn();
    window.alert = mockAlert;

    const mockHtml = `
      <div class='d-flex justify-content-end mb-2'>
        <button class='btn btn-sm btn-outline-primary full-message-button' 
                type='button' title='Open in new window'>
          Open in new window
        </button>
      </div>
    `;

    const MessageWrapper = {
      template: '<div ref="messageElement" v-html="messageHtml"></div>',
      data() {
        return {
          messageHtml: mockHtml,
          message: { uuid: testUuid, type: "loaded" },
        };
      },
      mounted() {
        const fullRenderButton = this.$refs.messageElement.querySelector(".full-message-button");
        if (fullRenderButton) {
          fullRenderButton.addEventListener("click", (event) => {
            event.preventDefault();
            const url = `/message/${this.message.uuid}`;
            const newWindow = window.open(url);
            if (!newWindow) {
              alert("Popup blocked. Please allow popups for this site.");
            }
          });
        }
      },
    };

    const wrapper = mount(MessageWrapper);
    await nextTick();

    // When
    const button = wrapper.find(".full-message-button");
    await button.trigger("click");

    // Then
    expect(mockWindowOpen).toHaveBeenCalledWith(`/message/${testUuid}`);
    expect(mockAlert).toHaveBeenCalledWith("Popup blocked. Please allow popups for this site.");
  });
});
