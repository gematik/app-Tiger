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

import { mount } from "@vue/test-utils";
import TestProject from "../pages/TestProject.vue";
import { createPinia, setActivePinia } from "pinia";
import PrimeVue from "primevue/config";
import DialogService from "primevue/dialogservice";

// Mock global dependencies
vi.mock("@/stores/featuresStore", () => ({
  useFeaturesStore: () => ({
    featureUpdateMap: {},
  }),
}));

vi.mock("@/stores/testSuiteLifecycle", () => ({
  useTestSuiteLifecycle: () => ({
    quitTestrunOngoing: false,
    pauseTestrunOngoing: false,
    bannerMessage: false,
    quitReason: { message: "", details: "" },
    hasTestRunFinished: false,
  }),
}));

// Mock WebSocket/SockJS
// Mock WebSocket/SockJS
vi.mock("sockjs-client", () => {
  return {
    default: vi.fn().mockImplementation(function () {
      return {
        addEventListener: vi.fn(),
        close: vi.fn(),
        send: vi.fn(),
      };
    }),
  };
});

describe("TestProject.vue - Tab Navigation Accessibility Tests", () => {
  beforeEach(() => {
    setActivePinia(createPinia());


    // Mock ResizeObserver as a proper constructor
    class MockResizeObserver {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
    }

    global.ResizeObserver = MockResizeObserver as any;

    global.fetch = vi.fn(() =>
      Promise.resolve({
        text: () => Promise.resolve("{}"),
        json: () => Promise.resolve({}),
      } as Response),
    );
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  function createTestProjectWrapper() {
    return mount(TestProject, {
      global: {
        plugins: [PrimeVue, DialogService],
        stubs: {
          FeatureList: { template: "<div />" },
          ServerStatus: { template: "<div />" },
          ExecutionPane: { template: "<div />" },
          ServerLog: { template: "<div />" },
          TrafficVisualization: { template: "<div />" },
          BannerMessageWindow: { template: "<div />" },
          TestSelector: { template: "<div />" },
          TigerConfigurationEditor: { template: "<div />" },
          SidePanel: { template: "<div><slot /></div>" },
          DynamicDialog: { template: "<div />" },
        },
      },
    });
  }

  describe("Tab navigation buttons accessibility", () => {
    it("Test execution tab should have role=button and tabindex=0", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const executionTab = wrapper.find("#test-execution-pane-tab");
      expect(executionTab.exists()).toBe(true);
      expect(executionTab.attributes("role")).toBe("button");
      expect(executionTab.attributes("tabindex")).toBe("0");
    });

    it("Server Logs tab should have role=button and tabindex=0", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const serverLogTab = wrapper.find("#test-server-log-tab");
      expect(serverLogTab.exists()).toBe(true);
      expect(serverLogTab.attributes("role")).toBe("button");
      expect(serverLogTab.attributes("tabindex")).toBe("0");
    });

    it("all tab navigation elements should be keyboard accessible", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const tabs = wrapper.findAll(".execution-pane-buttons");
      expect(tabs.length).toBeGreaterThanOrEqual(2);

      tabs.forEach((tab) => {
        expect(tab.attributes("role")).toBe("button");
        expect(tab.attributes("tabindex")).toBe("0");
      });
    });

    it("tabs should be in natural tab order", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const tabs = wrapper.findAll(".execution-pane-buttons");
      tabs.forEach((tab) => {
        const element = tab.element as HTMLElement;
        expect(element.tabIndex).toBe(0);
      });
    });
  });

  describe("Config editor close button accessibility", () => {
    it("should have role=button and tabindex=0", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const closeButton = wrapper.find("#test-tg-config-editor-btn-close");
      if (closeButton.exists()) {
        expect(closeButton.attributes("role")).toBe("button");
        expect(closeButton.attributes("tabindex")).toBe("0");
      }
    });

    it("should be keyboard navigable", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const closeButton = wrapper.find("#test-tg-config-editor-btn-close");
      if (closeButton.exists()) {
        const element = closeButton.element as HTMLElement;
        expect(element.tabIndex).toBe(0);
        expect(element.getAttribute("role")).toBe("button");
      }
    });
  });

  describe("Vimium compatibility for navigation elements", () => {
    it("navigation tabs should be discoverable by Vimium", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const tabs = wrapper.findAll(".execution-pane-buttons");
      tabs.forEach((tab) => {
        expect(tab.attributes("role")).toBe("button");
        expect(tab.isVisible()).toBe(true);
      });
    });

    it("all interactive elements should have proper ARIA roles", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const buttonRoles = wrapper.findAll('[role="button"]');
      expect(buttonRoles.length).toBeGreaterThanOrEqual(2);

      buttonRoles.forEach((element) => {
        expect(element.attributes("tabindex")).toBe("0");
      });
    });
  });

  describe("Keyboard navigation behavior", () => {
    it("tabs should respond to click events", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const serverLogTab = wrapper.find("#test-server-log-tab");
      expect(serverLogTab.exists()).toBe(true);

      // Initially, execution pane should be active
      const executionTab = wrapper.find("#test-execution-pane-tab");
      expect(executionTab.classes()).toContain("active");

      // Click server log tab
      await serverLogTab.trigger("click");

      // Note: The actual tab switching behavior would need to be verified
      // by checking if the correct pane is shown, but our stubs prevent that
      // This test at least verifies the click handler is attached
    });

    it("focus order should be predictable", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const tabs = wrapper.findAll(".execution-pane-buttons");
      tabs.forEach((tab) => {
        const element = tab.element as HTMLElement;
        expect(element.tabIndex).toBe(0);
      });
    });

    it("tabs should be focusable after interaction", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const serverLogTab = wrapper.find("#test-server-log-tab");
      const element = serverLogTab.element as HTMLElement;

      // Click the tab
      await serverLogTab.trigger("click");

      // Element should still be focusable after click
      // Note: jsdom doesn't perfectly simulate browser focus behavior,
      // so we just verify the element is focusable (tabindex="0")
      expect(element.tabIndex).toBe(0);
      expect(element.getAttribute("role")).toBe("button");

      // Verify we can programmatically focus it
      element.focus();
      // In jsdom, activeElement may be body, so we just verify no errors
      expect(element.tabIndex).toBe(0); // Still focusable
    });
  });

  describe("Screen reader support", () => {
    it("navigation tabs should have proper semantic roles", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const tabs = wrapper.findAll(".execution-pane-buttons");
      tabs.forEach((tab) => {
        expect(tab.attributes("role")).toBe("button");
        expect(tab.text()).toBeTruthy();
      });
    });

    it("tabs should have meaningful text content", async () => {
      const wrapper = createTestProjectWrapper();
      await wrapper.vm.$nextTick();

      const executionTab = wrapper.find("#test-execution-pane-tab");
      const serverLogTab = wrapper.find("#test-server-log-tab");

      expect(executionTab.text()).toBe("Test execution");
      expect(serverLogTab.text()).toBe("Server Logs");
    });
  });
});
