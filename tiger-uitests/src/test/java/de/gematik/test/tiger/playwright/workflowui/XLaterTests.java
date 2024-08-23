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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Dynamic tests of the workflow ui, that can only be tested when the feature file has run through
 * at a later time.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class XLaterTests extends AbstractTests {

  @Test
  void testENavbarWithButtonsExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertAll(
        () -> assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#webui-navbar")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-hide-button")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filterModalBtn")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs").isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-page-selection")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#pageNumberDisplay")
                        .textContent())
                .endsWith("1"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#pageSizeDisplay")
                        .textContent())
                .endsWith("20"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#dropdown-page-size")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#routeModalBtn")
                        .isVisible())
                .isFalse(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#scrollLockBtn")
                        .isVisible())
                .isFalse(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#resetMsgs").isVisible())
                .isFalse(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#importMsgs").isVisible())
                .isFalse());
  }

  @Test
  void testARbelMessagesExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            assertThat(
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
    await().untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
    externalPage.locator("#routeModalBtn").click();
    assertAll(
        () -> assertThat(externalPage.locator("#routeModalDialog").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#routingModalButtonClose").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#addNewRouteBtn").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#addNewRouteFromField").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#addNewRouteFromField").textContent()).isEmpty(),
        () -> assertThat(externalPage.locator("#addNewRouteToField").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#addNewRouteToField").textContent()).isEmpty());
    externalPage.locator("#routingModalButtonClose").click();
    assertThat(externalPage.locator("#routeModalDialog").isVisible()).isFalse();
    externalPage.close();
  }

  @Test
  void testBExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await().untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
    assertAll(
        () -> assertThat(externalPage.locator("#test-tiger-logo").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#routeModalBtn").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#scrollLockBtn").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#dropdown-hide-button").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#filterModalBtn").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#resetMsgs").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#exportMsgs").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#dropdown-page-selection").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#dropdown-page-size").isVisible()).isTrue(),
        () -> assertThat(externalPage.locator("#importMsgs").isVisible()).isTrue());
    externalPage.close();
  }

  @Test
  void testCServers() {
    page.querySelector("#test-tiger-logo").click();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-server-status-box").isVisible()).isTrue(),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-serverbox").count())
                .isEqualTo(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-name")
                        .count())
                .isEqualTo(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-status")
                        .count())
                .isEqualTo(3),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-url")
                        .count())
                .isEqualTo(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-url-icon")
                        .count())
                .isEqualTo(2),
        () ->
            assertThat(
                    page.locator("#test-sidebar-server-status-box .test-sidebar-server-log-icon")
                        .count())
                .isEqualTo(3));
  }
}
