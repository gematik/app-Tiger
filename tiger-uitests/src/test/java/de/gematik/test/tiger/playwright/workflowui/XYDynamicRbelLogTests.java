/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

/**
 * Tests for dynamic content of the web ui content, e.g. tests of all buttons, dropdowns, modals.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class XYDynamicRbelLogTests extends AbstractTests {

  @BeforeEach
  void printInfoStarted(TestInfo testInfo) {
    System.out.println("started = " + testInfo.getDisplayName());
  }

  @AfterEach
  void printInfoFinished(TestInfo testInfo) {
    System.out.println("finished = " + testInfo.getDisplayName());
  }

  @Test
  void testHExecutionPaneRbelWebUiURLExists() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#test-rbel-webui-url").isVisible()).isTrue();
  }

  @Test
  void testBRbelLogPaneOpensAndCloses() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
  }

  @Test
  void testDRbelLogPaneHideDetailsButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
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

    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageHeaderBtn")
                .isVisible())
        .isTrue();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageDetailsBtn")
                .isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-card-content.d-none")
                .count())
        .isEqualTo(20);
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageDetails.led-error")
                .isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
  }

  @Test
  void testERbelLogPaneHideHeaderButton() {
    page.locator("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
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

    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageHeaderBtn")
                .isVisible())
        .isTrue();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageDetailsBtn")
                .isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-msg-header-content.d-none")
                .count())
        .isEqualTo(20);
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-msg-body-content.d-none")
                .count())
        .isZero();
    page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator("#collapsibleMessageHeader.led-error")
                .isVisible())
        .isTrue();
    page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
  }

  @Test
  void testCExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() {
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
  void testFilterModalSetNonsenseFilter() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
    page.frameLocator("#rbellog-details-iframe")
        .locator("#setFilterCriterionInput")
        .fill("$.DOESNOTEXIST");
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
    await()
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText("0 of %s did match the filter criteria.".formatted(TOTAL_MESSAGES)));

    String content =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    String requestToContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent();
    String requestFromContent =
        page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent();
    int count =
        page.frameLocator("#rbellog-details-iframe")
            .locator("#test-rbel-section .test-msg-body-content")
            .count();
    page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("");
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertAll(
        () -> assertThat(requestToContent).contains("no request"),
        () -> assertThat(requestFromContent).contains("no request"),
        () ->
            assertThat(content)
                .isEqualTo("0 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES)),
        () -> assertThat(count).isZero());
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
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage"))
                    .hasText("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES)));
    String content =
        page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
    page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    assertThat(content)
        .isEqualTo("4 of %d did match the filter criteria.".formatted(TOTAL_MESSAGES));
  }

  @Test
  void testGSaveModal() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    page.frameLocator("#rbellog-details-iframe").locator("#exportMsgs").click();
    assertAll(
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveModalDialog")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveHtmlBtn")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveTrafficBtn")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveModalButtonClose")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveModalDialog .box")
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.frameLocator("#rbellog-details-iframe")
                        .locator("#saveModalDialog .box")
                        .allTextContents())
                .isNotEmpty());
    page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose").click();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog").isVisible())
        .isFalse();
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
        .until(() -> download.page().locator("#test-tiger-logo").isVisible());
    assertAll(
        () -> assertThat(download.page().locator("#test-tiger-logo").isVisible()).isTrue(),
        () ->
            assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            assertThat(
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
        .until(() -> download.page().locator("#test-tiger-logo").isVisible());
    assertAll(
        () -> assertThat(download.page().locator("#test-tiger-logo").isVisible()).isTrue(),
        () ->
            assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-header")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    download
                        .page()
                        .frameLocator("#rbellog-details-iframe")
                        .locator("#test-rbel-section .test-card-content")
                        .count())
                .isPositive());
  }
}
