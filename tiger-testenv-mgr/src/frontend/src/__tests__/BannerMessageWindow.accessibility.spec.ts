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
import BannerMessageWindow from "../components/testsuite/BannerMessageWindow.vue";
import BannerMessage from "../types/BannerMessage";
import BannerType from "../types/BannerType";
import type QuitReason from "../types/QuitReason";

describe("BannerMessageWindow.vue - Accessibility Tests", () => {
  const defaultQuitReason: QuitReason = {
    message: "Test quit",
    details: "Test details",
  };

  // Helper function to create BannerMessage instances with proper values
  function createBannerMessage(
    text: string,
    color: string,
    type: BannerType,
    isHtml = false,
  ): BannerMessage {
    const banner = new BannerMessage();
    banner.text = text;
    banner.color = color;
    banner.type = type;
    banner.isHtml = isHtml;
    banner.bannerDetails = null;
    return banner;
  }

  beforeEach(() => {
    // Setup DOM for getElementById calls in closeWindow function
    document.body.innerHTML = '<div id="workflow-messages"></div>';
  });

  afterEach(() => {
    document.body.innerHTML = "";
  });

  describe("Close buttons accessibility", () => {
    it("quit banner close button should have role=button and tabindex=0", () => {
      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: false,
          quitTestrunOngoing: true,
          quitReason: defaultQuitReason,
        },
      });

      const closeButton = wrapper.find(".btn-banner-close");
      expect(closeButton.exists()).toBe(true);
      expect(closeButton.attributes("role")).toBe("button");
      expect(closeButton.attributes("tabindex")).toBe("0");
    });

    it("message banner close button should have role=button and tabindex=0", () => {
      const messageBanner = createBannerMessage(
        "Test message",
        "blue",
        BannerType.MESSAGE,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: messageBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
      });

      const closeButton = wrapper.find(".btn-banner-close");
      expect(closeButton.exists()).toBe(true);
      expect(closeButton.attributes("role")).toBe("button");
      expect(closeButton.attributes("tabindex")).toBe("0");
    });

    it("close button should be actually focusable", () => {
      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: false,
          quitTestrunOngoing: true,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      const closeButton = wrapper.find(".btn-banner-close");
      const element = closeButton.element as HTMLElement;

      // Test that the button can actually receive focus
      element.focus();
      expect(document.activeElement).toBe(element);

      wrapper.unmount();
    });
  });

  describe("Continue button accessibility (STEP_WAIT)", () => {
    it("should have role=button and tabindex=0", async () => {
      const stepWaitBanner = createBannerMessage(
        "Please continue",
        "blue",
        BannerType.STEP_WAIT,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: stepWaitBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const continueButton = wrapper.find("div.btn-success");
      expect(continueButton.exists()).toBe(true);
      expect(continueButton.text()).toContain("Continue");
      expect(continueButton.attributes("role")).toBe("button");
      expect(continueButton.attributes("tabindex")).toBe("0");

      wrapper.unmount();
    });

    it("should be keyboard navigable", async () => {
      const stepWaitBanner = createBannerMessage(
        "Please continue",
        "blue",
        BannerType.STEP_WAIT,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: stepWaitBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const continueButton = wrapper.find("div.btn-success");
      const element = continueButton.element as HTMLElement;

      // Verify the element can be focused via keyboard
      expect(element.tabIndex).toBe(0);
      expect(element.getAttribute("role")).toBe("button");

      wrapper.unmount();
    });
  });

  describe("Pass and Fail buttons accessibility (FAIL_PASS)", () => {
    it("Pass button should have role=button and tabindex=0", async () => {
      const failPassBanner = createBannerMessage(
        "Did this pass?",
        "blue",
        BannerType.FAIL_PASS,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: failPassBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const passButton = wrapper.find("div.btn-success");
      expect(passButton.exists()).toBe(true);
      expect(passButton.text()).toContain("Pass");
      expect(passButton.attributes("role")).toBe("button");
      expect(passButton.attributes("tabindex")).toBe("0");

      wrapper.unmount();
    });

    it("Fail button should have role=button and tabindex=0", async () => {
      const failPassBanner = createBannerMessage(
        "Did this pass?",
        "blue",
        BannerType.FAIL_PASS,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: failPassBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const failButton = wrapper.find("div.btn-danger");
      expect(failButton.exists()).toBe(true);
      expect(failButton.text()).toContain("Fail");
      expect(failButton.attributes("role")).toBe("button");
      expect(failButton.attributes("tabindex")).toBe("0");

      wrapper.unmount();
    });

    it("both buttons should be keyboard navigable", async () => {
      const failPassBanner = createBannerMessage(
        "Did this pass?",
        "blue",
        BannerType.FAIL_PASS,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: failPassBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const passButton = wrapper.find("div.btn-success");
      const failButton = wrapper.find("div.btn-danger");

      // Verify Pass button
      const passElement = passButton.element as HTMLElement;
      expect(passElement.tabIndex).toBe(0);
      expect(passElement.getAttribute("role")).toBe("button");

      // Verify Fail button
      const failElement = failButton.element as HTMLElement;
      expect(failElement.tabIndex).toBe(0);
      expect(failElement.getAttribute("role")).toBe("button");

      wrapper.unmount();
    });

    it("buttons should be in the natural tab order", async () => {
      const failPassBanner = createBannerMessage(
        "Did this pass?",
        "blue",
        BannerType.FAIL_PASS,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: failPassBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      // Find all elements with tabindex="0"
      const tabbableElements = wrapper.findAll('[tabindex="0"]');

      // Should have at least Pass and Fail buttons
      expect(tabbableElements.length).toBeGreaterThanOrEqual(2);

      // Verify they have tabindex="0" which means natural tab order
      tabbableElements.forEach((el) => {
        expect(el.attributes("tabindex")).toBe("0");
      });

      wrapper.unmount();
    });
  });

  describe("Vimium compatibility", () => {
    it("all interactive buttons should be discoverable by tools like Vimium", async () => {
      const failPassBanner = createBannerMessage(
        "Did this pass?",
        "blue",
        BannerType.FAIL_PASS,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: failPassBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      // Vimium uses role="button" to identify clickable elements
      const buttonElements = wrapper.findAll('[role="button"]');

      // Should have Pass and Fail buttons
      expect(buttonElements.length).toBeGreaterThanOrEqual(2);

      // All should have both role and tabindex
      buttonElements.forEach((button) => {
        expect(button.attributes("role")).toBe("button");
        expect(button.attributes("tabindex")).toBe("0");
      });

      wrapper.unmount();
    });

    it("Continue button should be discoverable by Vimium", async () => {
      const stepWaitBanner = createBannerMessage(
        "Please continue",
        "blue",
        BannerType.STEP_WAIT,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: stepWaitBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      const continueButton = wrapper.find("div.btn-success");

      // Vimium requires role="button" to identify the element
      expect(continueButton.attributes("role")).toBe("button");

      // Additional check: element should be in the document and visible
      expect(continueButton.isVisible()).toBe(true);

      wrapper.unmount();
    });
  });

  describe("Screen reader accessibility", () => {
    it("interactive elements should be properly announced to screen readers", async () => {
      const stepWaitBanner = createBannerMessage(
        "Please continue",
        "blue",
        BannerType.STEP_WAIT,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: stepWaitBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
        attachTo: document.getElementById("workflow-messages") as HTMLElement,
      });

      await wrapper.vm.$nextTick();

      // Screen readers use role="button" to announce interactive elements
      const continueButton = wrapper.find("div.btn-success");
      expect(continueButton.attributes("role")).toBe("button");

      // The button text should be accessible
      expect(continueButton.text()).toBeTruthy();

      wrapper.unmount();
    });

    it("alert role should be present for accessibility", () => {
      const stepWaitBanner = createBannerMessage(
        "Please continue",
        "blue",
        BannerType.STEP_WAIT,
      );

      const wrapper = mount(BannerMessageWindow, {
        props: {
          bannerMessage: stepWaitBanner,
          quitTestrunOngoing: false,
          quitReason: defaultQuitReason,
        },
      });

      // The banner itself should have role="alert" for screen readers
      const alert = wrapper.find('[role="alert"]');
      expect(alert.exists()).toBe(true);
    });
  });
});
