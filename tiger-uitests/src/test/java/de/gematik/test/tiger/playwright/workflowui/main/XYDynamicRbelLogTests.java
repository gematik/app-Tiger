/*
 * Copyright 2025 gematik GmbH
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

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

/**
 * Tests for dynamic content of the web ui content, e.g. tests of all buttons, dropdowns, modals.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class XYDynamicRbelLogTests extends AbstractBase {

  @AfterEach
  void closeOpenModal() {
    var modalCloseButton =
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose");
    if (modalCloseButton.isVisible()) {
      modalCloseButton.click();
    }
  }

  @Test
  void testHExecutionPaneRbelWebUiURLExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#test-rbel-webui-url")).isVisible();
  }

  @Test
  void testBRbelLogPaneOpensAndCloses() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
  }

  @Test
  void testDRbelLogPaneHideDetailsButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#pageSelector .dropdown-item")
        .first()
        .click();
    await()
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last())
                    .containsText("20"));

    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar")).isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn"))
        .isVisible();
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn"))
        .isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-card-content.d-none"))
        .hasCount(20);
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageDetails.led-error"))
        .isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
  }

  @Test
  void testERbelLogPaneHideHeaderButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#pageSelector .dropdown-item")
        .first()
        .click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last())
                    .containsText("20"));

    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar")).isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn"))
        .isVisible();
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn"))
        .isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator(".test-msg-header-content.d-none"))
        .hasCount(20);
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator(".test-msg-body-content.d-none"))
        .hasCount(0);
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageHeader.led-error"))
        .isVisible();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
  }

  @Test
  void testCExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
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
  void testFilterModalSetNonsenseFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#setFilterCriterionInput")
        .fill("$.DOESNOTEXIST");
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText(
                        "Filter didn't match any of the %s messages.".formatted(TOTAL_MESSAGES)));

    Locator content = page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage");
    Locator requestToContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestToContent");
    Locator requestFromContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent");

    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("");
    assertAll(
        () -> assertThat(requestToContent).containsText("no request"),
        () -> assertThat(requestFromContent).containsText("no request"),
        () ->
            assertThat(content)
                .containsText(
                    "Filter didn't match any of the %d messages.".formatted(TOTAL_MESSAGES)),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-msg-body-content"))
                .hasCount(0));
  }

  @Test
  void testFilterModalSetFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#setFilterCriterionInput")
        .fill("$.body == \"hello=world\"");
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES)));
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
        .containsText("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES));
  }

  @Test
  void testGSaveModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs").click();
    assertAll(
        () ->
            assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog"))
                .isVisible(),
        () ->
            assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveHtmlBtn"))
                .isVisible(),
        () ->
            assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveTrafficBtn"))
                .isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose"))
                .isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog .box"))
                .isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog .box"))
                .not()
                .isEmpty());

    page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog"))
        .not()
        .isVisible();
  }

  @Test
  void testASaveModalDownloadHtml() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs").click();
    Download download =
        page.waitForDownload(
            () -> page.frameLocator("#rbellog-details-iframe").locator("#saveHtmlBtn").click());
    // wait for download to complete
    await()
        .pollDelay(100, TimeUnit.MILLISECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> download.page().locator("#test-tiger-logo").isVisible());
    assertAll(
        () -> assertThat(download.page().locator("#test-tiger-logo")).isVisible(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-content")
                        .count())
                .isPositive());
  }

  @Test
  void testASaveModalDownloadTgr() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs").click();
    Download download =
        page.waitForDownload(
            () -> page.frameLocator("#rbellog-details-iframe").locator("#saveTrafficBtn").click());
    // wait for download to complete
    await()
        .pollDelay(100, TimeUnit.MILLISECONDS)
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> download.page().locator("#test-tiger-logo").isVisible());
    assertAll(
        () -> assertThat(download.page().locator("#test-tiger-logo")).isVisible(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-content")
                        .count())
                .isPositive());
  }
}
