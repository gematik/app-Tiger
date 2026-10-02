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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.test.tiger.playwright.workflowui.main;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.*;
import com.microsoft.playwright.Locator.FilterOptions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tests for dynamic content of the web ui content, e.g. tests of all buttons, dropdowns, modals.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XYDynamicRbelLogTests extends AbstractBase {

  @AfterEach
  void closeOpenModal() {
    // Reset scenario scoping first: a test that fails/times out mid-toggle can leave it on (and
    // the iframe replaced by the "no messages" empty state), which would otherwise corrupt the
    // next test's starting state.
    resetScenarioScoping();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    var modalCloseButton = rbelFrameLocator.locator("#filterBackdrop .btn-close");
    if (modalCloseButton.isVisible()) {
      modalCloseButton.click();
    }
    resetFilter(rbelFrameLocator);
  }

  /**
   * The scoping toggle sits in the sidebar toolbar alongside quit, pause, and settings buttons, not
   * inside the embedded log's iframe - it applies to every scopable view (Rbel log, traffic
   * visualization, topology) at once.
   */
  private Locator scenarioScopeToggle() {
    return page.locator("#test-sidebar-scenario-scope-toggle");
  }

  private void resetScenarioScoping() {
    // the scope survives scenario switches, so it has to be switched off explicitly
    Locator toggle = scenarioScopeToggle();
    if (toggle.count() > 0 && "true".equals(toggle.getAttribute("aria-pressed"))) {
      toggle.click();
    }
    Locator selected = page.locator(".test-execution-pane-scenario-title.scenario-selected");
    if (selected.count() > 0) {
      selected.first().click();
    }
  }

  @Test
  @Order(10)
  @SneakyThrows
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

    // The assertions above run against the page that triggered the download. Only opening the
    // downloaded file itself shows whether the export carries its own provenance.
    try (Page exported = page.context().newPage()) {
      exported.navigate(download.path().toUri().toString());
      assertThat(exported.locator("#reportMetadataModalButton")).isVisible();
      exported.locator("#reportMetadataModalButton").click();
      assertThat(exported.locator("#reportMetadataModal")).isVisible();
      assertThat(exported.locator(".test-report-metadata-version")).not().isEmpty();
    }
  }

  @Test
  @Order(20)
  @SneakyThrows
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

    Path path = download.path();
    String firstJsonLine;
    try (var lines = java.nio.file.Files.lines(path)) {
      firstJsonLine =
          lines
              .filter(line -> line.trim().startsWith("{"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("No JSON line found in downloaded TGR file"));
    }

    JSONObject jsonObject = new JSONObject(firstJsonLine);
    Assertions.assertThat(jsonObject.has("tigerVersion")).isTrue();
    Assertions.assertThat(jsonObject.getString("tigerVersion")).isNotEmpty();

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
  @Order(30)
  void testBCheckScrollingToLastMessage() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    frameLocator.locator(".scroll-container").click();

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
        .withFailMessage("Pressed page down 20 times but didnt reach end of rbel log list!")
        .isLessThanOrEqualTo(200);
  }

  @Test
  @Order(40)
  void testBRbelLogPaneOpensAndCloses() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
  }

  @Test
  @Order(50)
  void testCExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    XLaterTests.checkNavBar(externalPage);
    externalPage.close();
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
  @Order(60)
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
  @Order(70)
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
  @Order(80)
  void testFFullMessageButton() {
    var sequenceNumber = "11";
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator(".test-message-number").first()));

    // in some ocasions, the global function scrollToMessage is not available. Probably it takes
    // longer to be attached
    // to the window object.
    externalPage.waitForFunction("() => typeof window.scrollToMessage === 'function'");
    externalPage.evaluate("scrollToMessage('', " + sequenceNumber + ")");

    Locator fullMessageButton =
        externalPage
            .locator(".rbel-message:has(.test-message-number:text-is('" + sequenceNumber + "'))")
            .locator(".full-message-button");
    // Wait until the virtual scroller has rendered the message and its button.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .ignoreExceptions()
        .until(fullMessageButton::isVisible);

    Page singleMessagePage =
        externalPage.waitForPopup(() -> fullMessageButton.evaluate("el => el.click()"));

    assertAll(
        () -> assertThat(singleMessagePage.locator("body")).isVisible(),
        () -> assertThat(singleMessagePage.locator(".full-message-button")).hasCount(0),
        () ->
            assertThat(singleMessagePage.locator(".test-message-number"))
                .containsText(sequenceNumber),
        () -> assertThat(singleMessagePage.locator("body")).not().containsText("redacted"));

    singleMessagePage.locator(".test-btn-inspect").first().click();
    assertThat(singleMessagePage.locator("#jexlQueryModal")).isVisible();

    singleMessagePage.locator("#rbelTreeExpressionTextArea").fill("$");
    singleMessagePage.locator("#jexlQueryModal .test-expression-button").click();
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);

    assertThat(singleMessagePage.locator("#jexlQueryModal .test-expression-success")).isVisible();

    singleMessagePage.locator("#jexlQueryModal .btn-close").click();
    assertThat(singleMessagePage.locator("#jexlQueryModal")).not().isVisible();

    singleMessagePage.locator(".test-modal-content").first().click();
    assertThat(singleMessagePage.locator("#rawContentModal")).isVisible();
    assertThat(singleMessagePage.locator("#rawContentModal")).not().containsText("redacted");
    singleMessagePage.locator("#rawContentModal .btn-close").click();
    assertThat(singleMessagePage.locator("#rawContentModal")).not().isVisible();

    Locator partnerMessageButton = singleMessagePage.locator(".partner-message-button").first();

    String currentSequenceNumber =
        singleMessagePage.locator(".test-message-number").first().textContent();

    partnerMessageButton.click();
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);

    String partnerSequenceNumber =
        singleMessagePage.locator(".test-message-number").first().textContent();
    Assertions.assertThat(partnerSequenceNumber).isNotEqualTo(currentSequenceNumber);

    assertAll(
        () -> assertThat(singleMessagePage.locator(".test-message-number")).isVisible(),
        () -> assertThat(singleMessagePage.locator("body")).not().containsText("redacted"));

    singleMessagePage.close();
    externalPage.close();
  }

  void resetFilter(FrameLocator frameLocator) {
    Locator resetButton = frameLocator.locator("#test-reset-filter-button");
    if (resetButton.isEnabled()) {
      resetButton.click();
      await()
          .atMost(5, TimeUnit.SECONDS)
          .until(() -> !frameLocator.locator(".message").first().innerText().equals("Loading..."));
    }
  }

  @Test
  @Order(90)
  void testFilterModalSetFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").isVisible();
    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").fill("$.body == \"hello=world\"");
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
  @Order(100)
  void testFilterSetNonsenseFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#rbelExpressionTextArea").isVisible();
    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").first().fill("$.DOESNOTEXIST");
    rbelFrameLocator.locator("#setFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(rbelFrameLocator.locator("#filteredMessage"))
                    .hasText("Matched 0 of %d".formatted(TOTAL_MESSAGES)));

    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").isVisible();
    Locator content = rbelFrameLocator.locator("#filteredMessage");
    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").fill(" ");
    assertAll(
        () ->
            assertThat(content)
                .containsText("Matched %d of %d".formatted(TOTAL_MESSAGES, TOTAL_MESSAGES)));
  }

  @Test
  @Order(110)
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
  @Order(120)
  void testHExecutionPaneRbelWebUiURLExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#test-rbel-webui-url")).isVisible();
  }

  @Test
  @Order(130)
  void testIRbelQueryModalHelpToggle() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var frameLocator = page.frameLocator("#rbellog-details-iframe");

    // Open first message details if not already open (click on card header).
    // Use JS click — the element is inside a virtual scroller whose CSS transforms
    // may position it outside the browser viewport.
    frameLocator.locator(".test-card").first().evaluate("el => el.click()");

    // Click inspect button in the details view
    frameLocator.locator(".test-btn-inspect").first().evaluate("el => el.click()");

    var modalLocator = frameLocator.locator("#jexlQueryModal");
    assertThat(modalLocator).isVisible();

    var toggleButton = modalLocator.locator(".msg-toggle");
    assertThat(toggleButton).isVisible();

    // Help content specific text
    var helpContent = modalLocator.locator("text=RBeL-Path is an expression language");

    // Store initial state
    boolean wasVisible = helpContent.isVisible();

    // Toggle
    toggleButton.click();

    // Check for state change
    if (wasVisible) {
      assertThat(helpContent).not().isVisible();
    } else {
      assertThat(helpContent).isVisible();
    }

    // Toggle back
    toggleButton.click();

    // Check for restoration
    if (wasVisible) {
      assertThat(helpContent).isVisible();
    } else {
      assertThat(helpContent).not().isVisible();
    }

    // Close modal
    modalLocator.locator(".btn-close").click();
  }

  @Test
  @Order(140)
  void testJScenarioOnlyToggleScopesToLastScenarioWithTraffic() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();

    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    Locator toggle = scenarioScopeToggle();
    assertThat(toggle).isVisible();
    assertThat(toggle).hasAttribute("aria-pressed", "false");
    assertThat(frameLocator.locator("#test-rbel-scenario-only-empty")).not().isVisible();

    // The fixture's last scenario ("Simple Get Request for scoped scenario test",
    // 02_PlaywrightTwoTest.feature) makes exactly one HTTP call, so scoped to it the log should
    // hold only its couple of messages - the very last message overall must already be visible
    // without any scrolling, unlike the unfiltered view (see testBCheckScrollingToLastMessage,
    // which needs up to 200 PageDowns to reach the same message).
    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "true");
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        frameLocator
                            .locator(".test-message-number")
                            .filter(
                                new FilterOptions()
                                    .setHasText(Pattern.compile("^" + TOTAL_MESSAGES + "$"))))
                    .isVisible());

    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "false");
    assertThat(frameLocator.locator("#test-rbel-scenario-only-empty")).not().isVisible();
  }

  @Test
  @Order(150)
  void testKExplicitSelectionScopesToScenarioFromFirstFeature() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();

    // "Simple Get Request" is the very first scenario of the whole fixture
    // (01_PlaywrightTest.feature),
    // so it's the first ".test-execution-pane-scenario-title" in DOM order - selecting it also
    // exercises the "next scenario" upper bound (TGR-2300), since it's not the last scenario.
    Locator firstScenarioTitle = page.locator(".test-execution-pane-scenario-title").first();
    assertThat(firstScenarioTitle).not().hasClass(Pattern.compile(".*scenario-selected.*"));
    firstScenarioTitle.click();
    assertThat(firstScenarioTitle).hasClass(Pattern.compile(".*scenario-selected.*"));

    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    Locator toggle = scenarioScopeToggle();
    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "true");

    // scoped to the first scenario, its own first message must be visible immediately...
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        frameLocator
                            .locator(".test-message-number")
                            .filter(new FilterOptions().setHasText(Pattern.compile("^1$"))))
                    .isVisible());

    // ...and the last message of the whole run must stay unreachable even after scrolling well
    // past what a handful of scoped messages would ever need (contrast with
    // testBCheckScrollingToLastMessage, which needs up to 200 PageDowns to reach it unfiltered).
    frameLocator.locator(".scroll-container").click();
    Keyboard keyboard = page.keyboard();
    for (int i = 0; i < 10; i++) {
      keyboard.press("PageDown");
    }
    assertThat(
            frameLocator
                .locator(".test-message-number")
                .filter(
                    new FilterOptions().setHasText(Pattern.compile("^" + TOTAL_MESSAGES + "$"))))
        .not()
        .isVisible();

    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "false");

    firstScenarioTitle.click();
    assertThat(firstScenarioTitle).not().hasClass(Pattern.compile(".*scenario-selected.*"));
  }

  @Test
  @Order(160)
  void testLTrafficVisualizationReflectsScenarioScoping() {
    page.querySelector("#test-execution-pane-tab").click();
    page.querySelector("#test-traffic-visualization-tab").click();

    // the sequence diagram re-renders straight from the (scoped or full) message list on every
    // change, unlike the topology diagram's edge store, which only ever adds edges and never
    // removes one it has already drawn - so scoping only narrows this view, not that one.
    Locator messages = page.locator("#visualization_pane .clickableMessageText");
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(messages).not().hasCount(0));
    int baselineCount = messages.count();

    page.querySelector("#test-execution-pane-tab").click();
    Locator firstScenarioTitle = page.locator(".test-execution-pane-scenario-title").first();
    firstScenarioTitle.click();
    assertThat(firstScenarioTitle).hasClass(Pattern.compile(".*scenario-selected.*"));
    Locator toggle = scenarioScopeToggle();
    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "true");

    page.querySelector("#test-traffic-visualization-tab").click();
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertThat(messages.count())
                    .as(
                        "scoped message count should shrink below the unscoped baseline (%d)",
                        baselineCount)
                    .isPositive()
                    .isLessThan(baselineCount));

    // scoping off brings the full diagram back
    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "false");
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(messages).hasCount(baselineCount));

    page.querySelector("#test-execution-pane-tab").click();
    firstScenarioTitle.click();
    assertThat(firstScenarioTitle).not().hasClass(Pattern.compile(".*scenario-selected.*"));
  }
}
