/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.playwright.workflowui.main;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Dynamic tests of the workflow ui, that can only be tested when the feature file has run through
 * at a later time.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class XLaterTests extends AbstractBase {

  @Test
  void testENavbarWithButtonsExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    XYDynamicRbelLogTests.checkTopNavbarWebUiInFrame(page).locator(".test-btn-settings").click();
  }

  @Test
  void testARbelMessagesExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    FrameLocator rbelFrame = page.frameLocator("#rbellog-details-iframe");
    assertAll(
        () ->
            Assertions.assertThat(rbelFrame.locator("#test-rbel-section .test-card").count())
                .withFailMessage("No rbel message at all found")
                .isPositive(),
        () ->
            Assertions.assertThat(rbelFrame.locator("#test-rbel-section .test-card-header").count())
                .withFailMessage("No rbel message header found")
                .isPositive(),
        () ->
            Assertions.assertThat(
                    rbelFrame.locator("#test-rbel-section .test-card-content").count())
                .withFailMessage("No rbel message content found")
                .isPositive());
  }

  private Page checkTopNavbarWebUi() {
    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    checkNavBar(externalPage);
    return externalPage;
  }

  public static void checkNavBar(Page externalPage) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator(".test-btn-sort")));
    assertAll(
        () -> assertThat(externalPage.locator(".test-btn-settings")).isVisible(),
        () -> assertThat(externalPage.locator(".test-input-filter")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-reset-filter")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-search")).isVisible());

    externalPage.locator(".test-btn-settings").click();
    assertAll(
        () -> assertThat(externalPage.locator(".test-check-hide-header")).isVisible(),
        () -> assertThat(externalPage.locator(".test-check-hide-details")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-export")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-config-routes")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-clear-messages")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-quit-proxy")).isVisible());
  }

  @Test
  void testBRoutingModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = checkTopNavbarWebUi();
    assertAll(
        () -> assertThat(externalPage.locator(".test-check-hide-header")).isVisible(),
        () -> assertThat(externalPage.locator(".test-check-hide-details")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-export")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-config-routes")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-clear-messages")).isVisible(),
        () -> assertThat(externalPage.locator(".test-btn-quit-proxy")).isVisible());
    externalPage.locator(".test-btn-config-routes").click();
    assertThat(externalPage.locator("#routeModal")).isVisible();
    externalPage.locator("#routeModal").locator(".btn-close").click();
    assertThat(externalPage.locator("#routeModal")).not().isVisible();
    externalPage.close();
  }

  @Test
  void testBExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = checkTopNavbarWebUi();
    if (externalPage.locator(".test-btn-export").isVisible()) {
      externalPage.locator(".test-btn-settings").click();
    }
    externalPage.close();
  }

  @Test
  void testCServers() {
    openSidebar();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-server-status-box")).isVisible(),
        () ->
            assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-serverbox"))
                .hasCount(3),
        () ->
            assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-name"))
                .hasCount(3),
        () ->
            assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-status"))
                .hasCount(3),
        () ->
            assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-url"))
                .hasCount(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-url-icon"))
                .hasCount(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-log-icon"))
                .hasCount(3));
  }
}
