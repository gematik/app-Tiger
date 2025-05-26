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

import com.microsoft.playwright.Download;
import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.FilterOptions;
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
        page.frameLocator("#rbellog-details-iframe").locator("#filterBackdrop .btn-close");
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

  public static FrameLocator checkTopNavbarWebUiInFrame(Page page) {
    FrameLocator frameLocator = page.frameLocator("#rbellog-details-iframe");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(frameLocator.locator(".test-btn-sort")));
    assertAll(
        () -> assertThat(frameLocator.locator(".test-btn-settings")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-input-filter")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-reset-filter")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-search")).isVisible());

    frameLocator.locator(".test-btn-settings").click();
    assertThat(frameLocator.locator(".test-btn-quit-proxy")).isVisible();
    assertAll(
        () -> assertThat(frameLocator.locator(".test-check-hide-header")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-check-hide-details")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-export")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-config-routes")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-clear-messages")).isVisible(),
        () -> assertThat(frameLocator.locator(".test-btn-quit-proxy")).isVisible());
    return frameLocator;
  }

  @Test
  void testDRbelLogPaneHideDetailsButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    FrameLocator frameLocator = checkTopNavbarWebUiInFrame(page);
    frameLocator.locator("#hideDetails").click();
    frameLocator.locator("#test-settings-button").click();

    assertThat(frameLocator.locator(".test-card-content.d-none").first()).not().isVisible();

    frameLocator.locator("#test-settings-button").click();
    frameLocator.locator("#hideDetails").click();
    frameLocator.locator("#test-settings-button").click();
  }

  @Test
  void testERbelLogPaneHideHeaderButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    FrameLocator frameLocator = checkTopNavbarWebUiInFrame(page);

    frameLocator.locator("#hideHeader").click();
    frameLocator.locator("#test-settings-button").click();

    assertThat(frameLocator.locator(".test-msg-header-content.d-none").first()).not().isVisible();
    assertThat(frameLocator.locator(".test-msg-body-content.d-none")).hasCount(0);

    frameLocator.locator("#test-settings-button").click();
    frameLocator.locator("#hideHeader").click();
    frameLocator.locator("#test-settings-button").click();
  }

  @Test
  void testCExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    XLaterTests.checkNavBar(externalPage);
    externalPage.close();
  }

  @Test
  void testFilterSetNonsenseFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#filterBackdrop #rbelExpressionTextArea").isVisible();
    rbelFrameLocator
        .locator("#filterBackdrop #rbelFilterExpressionTextArea")
        .first()
        .fill("$.DOESNOTEXIST");
    rbelFrameLocator.locator("#setFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(rbelFrameLocator.locator("#filteredMessage"))
                    .hasText("Matched 0 of %d".formatted(TOTAL_MESSAGES)));

    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").isVisible();
    Locator content = rbelFrameLocator.locator("#filteredMessage");
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").fill(" ");
    assertAll(
        () ->
            assertThat(content)
                .containsText("Matched %d of %d".formatted(TOTAL_MESSAGES, TOTAL_MESSAGES)));
  }

  @Test
  void testFilterModalSetFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").isVisible();
    rbelFrameLocator
        .locator("#filterBackdrop #rbelFilterExpressionTextArea")
        .fill("$.body == \"hello=world\"");
    rbelFrameLocator.locator("#setFilterCriterionBtn").click();
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(rbelFrameLocator.locator("#filteredMessage"))
                    .hasText("Matched 4 of %d".formatted(TOTAL_MESSAGES)));
    assertThat(rbelFrameLocator.locator("#filteredMessage"))
        .containsText("Matched 4 of %d".formatted(TOTAL_MESSAGES));
  }

  @Test
  void testGExportModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#test-settings-button").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportModalButton").click();
    assertAll(
        () ->
            assertThat(page.frameLocator("#rbellog-details-iframe").locator("#exportModal"))
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
                .isVisible());

    page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#exportModal"))
        .not()
        .isVisible();
  }

  @Test
  void testASaveModalDownloadHtml() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#test-settings-button").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportModalButton").click();
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
    page.frameLocator("#rbellog-details-iframe").locator("#test-settings-button").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportModalButton").click();
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

  @Test
  void testBCheckScrollingToLastMessage() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    frameLocator.locator("#test-rbel-section").first().click();

    Keyboard keyboard = page.keyboard();

    int ctr = 0;
    while (++ctr < 200
        && !frameLocator
            .locator(".test-message-number")
            .filter(new FilterOptions().setHasText(String.valueOf(TOTAL_MESSAGES)))
            .isVisible()) {
      keyboard.press("PageDown");
      await().pollDelay(200, TimeUnit.MILLISECONDS).until(() -> true);
    }
    Assertions.assertThat(ctr)
        .isLessThanOrEqualTo(200)
        .withFailMessage("Pressed page down 20 times but didnt reach end of rbel log list!");
  }
}
