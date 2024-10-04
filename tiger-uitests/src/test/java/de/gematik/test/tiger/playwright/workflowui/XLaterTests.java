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
 */

package de.gematik.test.tiger.playwright.workflowui;


import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Page;
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
    assertAll(
        () -> assertThat(page.locator("#rbellog_details_pane")).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#webui-navbar")
                        ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-hide-button")
                        ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filterModalBtn")
                        ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs")).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-page-selection")
                        ).isVisible(),
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#pageNumberDisplay")
                        .textContent())
                .endsWith("1"),
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#pageSizeDisplay")
                        .textContent())
                .endsWith("20"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-page-size")
                        ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#routeModalBtn")
                        ).not().isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#scrollLockBtn")
                        ).not().isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#resetMsgs")).not().isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#importMsgs")).not().isVisible());
  }

  @Test
  void testARbelMessagesExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertAll(
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-content")
                        .count())
                .isPositive());
  }

  @Test
  void testBRoutingModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
    externalPage.locator("#routeModalBtn").click();
    assertAll(
        () -> assertThat(externalPage.locator("#routeModalDialog")).isVisible(),
        () -> assertThat(externalPage.locator("#routingModalButtonClose")).isVisible(),
        () -> assertThat(externalPage.locator("#addNewRouteBtn")).isVisible(),
        () -> assertThat(externalPage.locator("#addNewRouteFromField")).isVisible(),
        () -> assertThat(externalPage.locator("#addNewRouteFromField")).isEmpty(),
        () -> assertThat(externalPage.locator("#addNewRouteToField")).isVisible(),
        () -> assertThat(externalPage.locator("#addNewRouteToField")).isEmpty());
    externalPage.locator("#routingModalButtonClose").click();
    assertThat(externalPage.locator("#routeModalDialog")).not().isVisible();
    externalPage.close();
  }

  @Test
  void testBExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
    assertAll(
        () -> assertThat(externalPage.locator("#test-tiger-logo")).isVisible(),
        () -> assertThat(externalPage.locator("#routeModalBtn")).isVisible(),
        () -> assertThat(externalPage.locator("#scrollLockBtn")).isVisible(),
        () -> assertThat(externalPage.locator("#dropdown-hide-button")).isVisible(),
        () -> assertThat(externalPage.locator("#filterModalBtn")).isVisible(),
        () -> assertThat(externalPage.locator("#resetMsgs")).isVisible(),
        () -> assertThat(externalPage.locator("#exportMsgs")).isVisible(),
        () -> assertThat(externalPage.locator("#dropdown-page-selection")).isVisible(),
        () -> assertThat(externalPage.locator("#dropdown-page-size")).isVisible(),
        () -> assertThat(externalPage.locator("#importMsgs")).isVisible());
    externalPage.close();
  }

  @Test
  void testCServers() {
    openSidebar();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-server-status-box")).isVisible(),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-serverbox")).hasCount(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-name")).hasCount(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-status")).hasCount(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-url")).hasCount(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-url-icon")).hasCount(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-log-icon")).hasCount(3));
  }
}
