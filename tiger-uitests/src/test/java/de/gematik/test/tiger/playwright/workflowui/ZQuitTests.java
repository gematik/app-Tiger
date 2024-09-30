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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

/**
 * These tests should run at the very last because the testQuitButton() quits the tiger/workflowui.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class ZQuitTests extends AbstractBase {
  @Test
  void testQuitMessageOnSidebar() {
    page.querySelector("#test-tiger-logo").click();
    assertThat(page.locator("#test-sidebar-stop-message")).isVisible();
    assertThat(page.locator("#test-sidebar-stop-message")).isVisible();
  }

  @Test
  void testClickOnLastRequestChangesPageNumberInRbelLogDetails() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#pageSelector .dropdown-item")
        .first()
        .click();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#sizeSelector .dropdown-item")
        .nth(1)
        .click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
    page.locator(".test-rbel-link").first().click();
    List<String> allNumbers = page.locator(".test-rbel-link").allTextContents();
    String number1 = allNumbers.get(0);
    String number2 = String.valueOf(Integer.parseInt(allNumbers.get(allNumbers.size() - 1)) + 1);

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .first()));
    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .first()).containsText(number1),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .last()).not().containsText(number2));
    String pageNo =
        page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
    page.locator(".test-rbel-link").last().click();
    // somehow I need to wait
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay")));
    String pageNo2 =
        page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
    assertAll(
        () -> Assertions.assertThat(pageNo).isNotEqualTo(pageNo2),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .first()).not().equals(number1),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .last()).equals(number2));
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
                            .last()).containsText("20"));
  }

  @Test
  void testAFilterModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
    page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filterModalDialog")
                        ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionInput")
            ).isVisible(),
        () ->
            Assertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionInput")
                        .textContent())
                .isEmpty(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestFromContent")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestToContent")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestToContent")
            ).containsText("no request"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestFromContent")
            ).containsText("no request"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#resetFilterCriterionBtn")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionBtn")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filteredMessage")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filterModalButtonClose")
            ).isVisible(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                .hasText("Filter matched to all of the %d messages.".formatted(TOTAL_MESSAGES)));
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator("#filterModalDialog")).not().isVisible();
  }

  @Test
  void testAFilterModalResetFilter() {
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
                assertThat(
                        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES)));
    String filteredMessage =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    int count =
        page.frameLocator("#rbellog-details-iframe")
            .locator("#test-rbel-section .test-msg-body-content")
            .count();
    page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText(
                        "Filter matched to all of the %d messages.".formatted(TOTAL_MESSAGES)));

    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("");
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    Assertions.assertThat(filteredMessage)
        .isEqualTo("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES));
    Assertions.assertThat(count).isEqualTo(3);
    assertThat(
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
        .hasText(
            "Filter matched to all of the %d messages.".formatted(TOTAL_MESSAGES));
  }

  @Test
  void testAFilterModalSetSenderFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#setFilterCriterionInput")
        .fill("$.sender == \"put\"");
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe").locator("#requestToContent")));
    Locator requestToContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestToContent");
    Locator requestFromContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent");
    Locator filteredMessage =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage");
    page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertAll(
        () -> assertThat(requestToContent).containsText("no request"),
        () -> assertThat(requestFromContent).containsText("no request"),
        () ->
            assertTrue(
                filteredMessage.innerText().equals(
                        "Filter matched to all of the %d messages.".formatted(TOTAL_MESSAGES))));
  }

  @Test
  void testXResetButton() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> Assertions.assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive());
    Assertions.assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive();
    externalPage.locator("#resetMsgs").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(externalPage.locator("#rbelmsglist .test-card")).hasCount(0));
    assertThat(externalPage.locator("#rbelmsglist .test-card")).hasCount(0);
    externalPage.close();
  }

  @Test
  void testZQuitButton() {
    page.querySelector("#test-tiger-logo").click();
    page.querySelector("#test-sidebar-quit-icon").click();
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> page.querySelector("#workflow-messages.test-messages-quit") != null);
    assertAll(
        () ->
            assertThat(page.locator("#sidebar-left.test-sidebar-quit")).isVisible(),
        () ->
            assertThat(page.locator("#workflow-messages.test-messages-quit")).isVisible());
    page.querySelector("#test-tiger-logo").click();
    page.screenshot(
        new Page.ScreenshotOptions().setFullPage(false).setPath(getPath("workflowui_quit.png")));
  }

  @Test
  void testPageButton() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
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
                            .last()).containsText("20"));

    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .first()).containsText("1");
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#pageSelector .dropdown-item")).hasCount(4);
    page.frameLocator("#rbellog-details-iframe")
        .locator("#pageSelector .dropdown-item")
        .last()
        .click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .first()).containsText("41"));
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .first()).containsText("41");
  }

  @Test
  void testSizeButton() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
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
                            .last()).containsText("20"));

    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .last()).containsText("20");
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#sizeSelector .dropdown-item")).hasCount(4);
    page.frameLocator("#rbellog-details-iframe")
        .locator("#sizeSelector .dropdown-item")
        .last()
        .click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last())
                    .hasText(String.valueOf(TOTAL_MESSAGES)));
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .last()).containsText(String.valueOf(TOTAL_MESSAGES));
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#sizeSelector .dropdown-item")
        .nth(1)
        .click();
  }
}
