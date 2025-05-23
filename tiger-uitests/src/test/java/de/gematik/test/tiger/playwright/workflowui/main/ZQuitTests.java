/*
 *
 * Copyright 2021-2025 gematik GmbH
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

import com.microsoft.playwright.Page;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
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

  @Test
  void testQuitMessageOnSidebarExists() {
    openSidebar();
    assertThat(page.locator("#test-sidebar-stop-message")).isVisible();
  }

  @Test
  void testClickOnLastRequestChangesPageNumberInRbelLogDetails() {
    // TODO navigate to last message via cursor down key?
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
    String filteredMessage =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    rbelFrameLocator.locator("#filterBackdrop .btn-close").click();

    // varify count is correct
    int count =
        page.frameLocator("#rbellog-details-iframe")
            .locator("#test-rbel-section .test-msg-body-content")
            .count();
    Assertions.assertThat(count).isEqualTo(3);

    // reset filter via button
    page.frameLocator("#rbellog-details-iframe").locator("#test-reset-filter-button").click();

    // check input field is empty and all messages shown
    // rbelFrameLocator.locator("#test-rbel-path-input").click();
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
    rbelFrameLocator.locator("#filterBackdrop #rbelFilterExpressionTextArea").isVisible();

    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("ul.test-select-recipient li a")
                        .last())
                .hasText("httpbin:80"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("ul.test-select-sender li a")
                        .last())
                .hasText("httpbin:80"));

    rbelFrameLocator
        .locator("#filterBackdrop #rbelFilterExpressionTextArea")
        .first()
        .fill("$.sender == \"put\"");
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
                .not()
                .isAttached(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("ul.test-select-sender li"))
                .not()
                .isAttached());
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
