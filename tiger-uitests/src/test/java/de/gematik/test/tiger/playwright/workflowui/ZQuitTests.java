/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

/**
 * These tests should run at the very last because the testQuitButton() quits the tiger/workflowui.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class ZQuitTests extends AbstractTests {
  @BeforeEach
  void printInfoStarted(TestInfo testInfo) {
    System.out.println("started = " + testInfo.getDisplayName());
  }

  @AfterEach
  void printInfoFinished(TestInfo testInfo) {
    System.out.println("finished = " + testInfo.getDisplayName());
  }

  @Test
  void testQuitMessageOnSidebar() {
    page.querySelector("#test-tiger-logo").click();
    assertThat(page.querySelector("#test-sidebar-stop-message").isVisible()).isTrue();
    PlaywrightAssertions.assertThat(page.locator("#test-sidebar-stop-message")).isVisible();
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
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
    page.locator(".test-rbel-link").first().click();
    List<String> allNumbers = page.locator(".test-rbel-link").allTextContents();
    String number1 = allNumbers.get(0);
    String number2 = String.valueOf(Integer.parseInt(allNumbers.get(allNumbers.size() - 1)) + 1);

    await()
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
                        .first()
                        .textContent())
                .isEqualTo(number1),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .last()
                        .textContent())
                .isNotEqualTo(number2));
    String pageNo =
        page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
    page.locator(".test-rbel-link").last().click();
    // somehow I need to wait
    await()
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay")));
    String pageNo2 =
        page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
    assertAll(
        () -> assertThat(pageNo).isNotEqualTo(pageNo2),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .first()
                        .textContent())
                .isNotEqualTo(number1),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator(".test-message-number")
                        .last()
                        .textContent())
                .isEqualTo(String.valueOf(number2)));
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
                            .last()
                            .textContent())
                    .isEqualTo("20"));
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
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionInput")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionInput")
                        .textContent())
                .isEmpty(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestFromContent")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestToContent")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestToContent")
                        .textContent())
                .contains("no request"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#requestFromContent")
                        .textContent())
                .contains("no request"),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#resetFilterCriterionBtn")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#setFilterCriterionBtn")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filteredMessage")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#filterModalButtonClose")
                        .isVisible())
                .isTrue(),
        () ->
            PlaywrightAssertions.assertThat(
                    page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                .hasText("Filter didn't match any of the %d messages.".formatted(TOTAL_MESSAGES)));
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe").locator("#filterModalDialog").isVisible())
        .isFalse();
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
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
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
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText(
                        "Filter didn't match any of the %d messages.".formatted(TOTAL_MESSAGES)));
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("");
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertThat(filteredMessage)
        .isEqualTo("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES));
    assertThat(count).isEqualTo(3);
    PlaywrightAssertions.assertThat(
            page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
        .hasText("Filter didn't match any of the %d messages.".formatted(TOTAL_MESSAGES));
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
        .untilAsserted(
            () ->
                assertNotNull(
                    page.frameLocator("#rbellog-details-iframe").locator("#requestToContent")));
    String requestToContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent();
    String requestFromContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent();
    String filteredMessage =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertAll(
        () -> assertThat(requestToContent).contains("no request"),
        () -> assertThat(requestFromContent).contains("no request"),
        () ->
            assertTrue(
                filteredMessage.equals(
                        "0 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES))
                    || filteredMessage.equals(
                        "Filter didn't match any of the %d messages.".formatted(TOTAL_MESSAGES))));
  }

  @Test
  void testXResetButton() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .untilAsserted(
            () -> assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive());
    assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive();
    externalPage.locator("#resetMsgs").click();
    await()
        .untilAsserted(
            () -> assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isZero());
    assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isZero();
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
            assertThat(page.querySelector("#sidebar-left.test-sidebar-quit").isVisible()).isTrue(),
        () ->
            assertThat(page.querySelector("#workflow-messages.test-messages-quit").isVisible())
                .isTrue());
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
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last()
                            .textContent())
                    .isEqualTo("20"));

    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .first()
                .textContent())
        .isEqualTo("1");
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#pageSelector .dropdown-item")
                .count())
        .isEqualTo(3);
    page.frameLocator("#rbellog-details-iframe")
        .locator("#pageSelector .dropdown-item")
        .last()
        .click();
    await()
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .first()
                            .textContent())
                    .isEqualTo("41"));
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .first()
                .textContent())
        .isEqualTo("41");
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
        .untilAsserted(
            () ->
                assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last()
                            .textContent())
                    .isEqualTo("20"));

    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .last()
                .textContent())
        .isEqualTo("20");
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#sizeSelector .dropdown-item")
                .count())
        .isEqualTo(4);
    page.frameLocator("#rbellog-details-iframe")
        .locator("#sizeSelector .dropdown-item")
        .last()
        .click();
    await()
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.frameLocator("#rbellog-details-iframe")
                            .locator(".test-message-number")
                            .last())
                    .hasText(String.valueOf(TOTAL_MESSAGES)));
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .last()
                .textContent())
        .isEqualTo(String.valueOf(TOTAL_MESSAGES));
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#sizeSelector .dropdown-item")
        .nth(1)
        .click();
  }
}
