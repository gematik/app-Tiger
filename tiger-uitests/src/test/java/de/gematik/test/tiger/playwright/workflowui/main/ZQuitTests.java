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

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * These tests should run at the very last because the testQuitButton() quits the tiger/workflowui.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class ZQuitTests extends AbstractBase {

  private static final String LOADING_PLACEHOLDER = "Loading...";

  @AfterEach
  void closeOpenModal() {
    var modalCloseButton =
        page.frameLocator("#rbellog-details-iframe").locator("#filterBackdrop .btn-close");
    if (modalCloseButton.isVisible()) {
      modalCloseButton.click();
    }
    var resetButton =
        page.frameLocator("#rbellog-details-iframe").locator("#test-reset-filter-button");
    if (resetButton.isEnabled()) {
      resetButton.click();
    }
  }

  /**
   * We have a recycling view in the iframe. Components get mounted and unmounted while we scroll.
   * Any locator trying to find an element will not find elements that are not currently mounted.
   */
  Set<String> collectAllItemsFromIframe() {
    FrameLocator frame = page.frameLocator("#rbellog-details-iframe");

    Set<String> collected = new LinkedHashSet<>();
    Locator scrollable = frame.locator(".scroll-container");
    Locator items = frame.locator(".message");

    BooleanSupplier atBottom =
        () ->
            (Boolean)
                scrollable.evaluate(
                    "el => Math.ceil(el.scrollTop + el.clientHeight) >= el.scrollHeight");

    Runnable scrollStep =
        () ->
            scrollable.evaluate(
                "el => { const step = Math.max(100, el.clientHeight); el.scrollTop ="
                    + " Math.min(el.scrollTop + step, el.scrollHeight); }");

    while (!atBottom.getAsBoolean()) {
      List<Locator> filteredItems =
          items.all().stream().filter(i -> isInContainerViewport(i, scrollable)).toList();
      await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () ->
                  filteredItems.stream().noneMatch(i -> LOADING_PLACEHOLDER.equals(i.innerText())));

      await().atMost(5, TimeUnit.SECONDS).until(() -> !items.all().isEmpty());

      filteredItems.forEach(i -> collected.add(i.innerHTML().trim()));
      scrollStep.run();
    }

    // Once more once reached bottom
    List<Locator> filteredItems =
        items.all().stream().filter(i -> isInContainerViewport(i, scrollable)).toList();
    filteredItems.forEach(i -> collected.add(i.innerHTML().trim()));

    return collected;
  }

  private static boolean isInContainerViewport(Locator item, Locator container) {
    var itemBox = item.boundingBox();
    var contBox = container.boundingBox();
    if (itemBox == null || contBox == null) return false; // not rendered or zero-sized
    double itemLeft = itemBox.x, itemRight = itemBox.x + itemBox.width;
    double itemTop = itemBox.y, itemBottom = itemBox.y + itemBox.height;
    double contLeft = contBox.x, contRight = contBox.x + contBox.width;
    double contTop = contBox.y, contBottom = contBox.y + contBox.height;

    boolean horizontallyOverlaps = itemLeft < contRight && itemRight > contLeft;
    boolean verticallyOverlaps = itemTop < contBottom && itemBottom > contTop;
    return horizontallyOverlaps && verticallyOverlaps;
  }

  @Test
  void testQuitMessageOnSidebarExists() {
    // close the HTML banner from scenario of second feature file to end the test run in the other
    // thread
    page.locator("#workflow-messages").locator(".btn-success").click();
    openSidebar();
    // wait for up to 2 minutes for test run in other thread to finish
    await()
        .atMost(120, TimeUnit.SECONDS)
        .alias("Wait for test stopped message")
        .pollInterval(1, TimeUnit.SECONDS)
        .until(() -> page.locator("#test-sidebar-stop-message").isVisible());
  }

  @Test
  void testAFilterModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    frameLocator.locator(".test-input-filter").click();
    assertAll(
        () ->
            assertThat(frameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea"))
                .isVisible(),
        () -> assertThat(frameLocator.locator("#filterBackdrop #filteredMessage")).isVisible(),
        () ->
            assertThat(frameLocator.locator("#filterBackdrop #setFilterCriterionBtn")).isVisible(),
        () -> assertThat(frameLocator.locator("#filteredMessage")).isVisible());
    page.frameLocator("#rbellog-details-iframe").locator("#filterBackdrop .btn-close").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filterBackdrop"))
        .not()
        .isVisible();
  }

  @Test
  void testAFilterModalResetFilter() {
    // open filter via click on input field and add filter rbel expression
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").isVisible();
    rbelFrameLocator
        .locator("#filterBackdrop #rbelFilterExpressionTextArea")
        .first()
        .fill("$.body == \"hello=world\"");
    // apply and reopen
    rbelFrameLocator.locator("#setFilterCriterionBtn").click();
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    // check filter message is correct and close modal
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(rbelFrameLocator.locator("#filteredMessage"))
                    .hasText("Matched 4 of %d".formatted(TOTAL_MESSAGES)));

    page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    rbelFrameLocator.locator("#filterBackdrop .btn-close").click();

    // varify count is correct
    var count = collectAllItemsFromIframe().size();
    Assertions.assertThat(count).isGreaterThanOrEqualTo(3);

    // reset filter via button
    page.frameLocator("#rbellog-details-iframe").locator("#test-reset-filter-button").click();

    // check input field is empty and all messages shown
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").isVisible();
    assertThat(rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea"))
        .hasValue("");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText("Matched %d of %d".formatted(TOTAL_MESSAGES, TOTAL_MESSAGES)));
  }

  @Test
  void testAFilterModalSetSenderFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    var rbelFrameLocator = page.frameLocator("#rbellog-details-iframe");
    rbelFrameLocator.locator("#test-rbel-path-input").click();
    assertThat(rbelFrameLocator.locator("#rbelFilterExpressionTextArea")).isVisible();

    assertAll(
        () -> {
          assertThat(
                  rbelFrameLocator.locator("ul.test-select-recipient li a").getByText("httpbin:80"))
              .hasCount(1);
        },
        () -> {
          assertThat(rbelFrameLocator.locator("ul.test-select-sender li a").getByText("httpbin:80"))
              .hasCount(1);
        });

    rbelFrameLocator.locator("#rbelFilterExpressionTextArea").first().fill("$.sender == \"put\"");
    // apply and reopen
    rbelFrameLocator.locator("#setFilterCriterionBtn").click();
    rbelFrameLocator.locator("#test-rbel-path-input").click();

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe").locator("#requestToContent")));
    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("ul.test-select-recipient li"))
                .hasCount(0),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("ul.test-select-sender li"))
                .hasCount(0));
  }

  @Test
  void testXResetButton() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertThat(externalPage.locator("#test-rbel-section .test-card").count())
                    .isPositive());
    externalPage.locator(".test-btn-settings").click();
    externalPage.locator(".test-btn-clear-messages").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(externalPage.locator("#test-rbel-section .test-card")).hasCount(0));
    assertThat(externalPage.locator("#test-rbel-section .test-card")).hasCount(0);
    externalPage.close();
  }

  @Test
  void testZQuitButton() {
    openSidebar();
    page.querySelector("#test-sidebar-quit-icon").click();
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> page.querySelector("#workflow-messages.test-messages-quit") != null);
    assertAll(
        () -> assertThat(page.locator("#sidebar-left.test-sidebar-quit")).isVisible(),
        () -> assertThat(page.locator("#workflow-messages.test-messages-quit")).isVisible());
    closeSidebar();
    page.screenshot(
        new Page.ScreenshotOptions().setFullPage(false).setPath(getPath("workflowui_quit.png")));
  }
}
