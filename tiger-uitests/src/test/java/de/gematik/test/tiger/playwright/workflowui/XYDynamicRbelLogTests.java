/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * Tests for dynamic content of the web ui content, e.g. tests of all buttons, dropdowns, modals.
 */
@TestMethodOrder(OrderAnnotation.class)
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
    @Order(12)
    void testExecutionPaneRbelWebUiURLExists() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#test-rbel-webui-url").isVisible()).isTrue();
    }

    @Test
    @Order(3)
    void testRbelLogPaneOpensAndCloses() {
        page.locator("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
    }

    @Test
    @Order(7)
    void testRbelLogPaneHideDetailsButton() {
        page.locator("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible()).isTrue();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").isVisible()).isTrue();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").isVisible()).isTrue();
        page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-card-content.d-none").count()).isEqualTo(4);
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
        page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").click();
    }

    @Test
    @Order(8)
    void testRbelLogPaneHideHeaderButton() {
        page.locator("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible()).isTrue();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").isVisible()).isTrue();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageDetailsBtn").isVisible()).isTrue();
        page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-msg-header-content.d-none").count()).isEqualTo(4);
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-msg-body-content.d-none").count()).isZero();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").click();
        page.frameLocator("#rbellog-details-iframe").locator("#collapsibleMessageHeaderBtn").click();
    }

    @Test
    @Order(6)
    void testExecutionPaneRbelOpenWebUiURLCheckNavBarButtons() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();

        Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(externalPage.locator("#test-tiger-logo")));
        assertAll(
            () -> assertThat(externalPage.locator("#test-tiger-logo").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#routeModalBtn").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#scrollLockBtn").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#dropdown-hide-button").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#filterModalBtn").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#resetMsgs").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#saveMsgs").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#dropdown-page-selection").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#dropdown-page-size").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#importMsgs").isVisible()).isTrue()
        );
        externalPage.close();
    }

    @Test
    @Order(9)
    void testFilterModalSetNonsenseFilter() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.body");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent")));

        String content = page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
        boolean value = content.equals("0 of 64 did match the filter criteria.") || content.equals(
            "Filter didn't match any of the 64 messages.");

        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent()).contains("no request"),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent()).contains("no request"),
            () -> assertThat(value).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-msg-body-content").count()).isZero()
        );
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    }

    @Test
    @Order(10)
    void testFilterModalSetReceiverFilter() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.receiver == \"put\"");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage")));
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("0 of 64 did match the filter criteria.");
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    }

    @Test
    @Order(13)
    void testSaveModal() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#saveMsgs").click();
        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveHtmlBtn").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveTrafficBtn").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog .box").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog .box").allTextContents()).isNotEmpty()
            );
        page.frameLocator("#rbellog-details-iframe").locator("#saveModalButtonClose").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveModalDialog").isVisible()).isFalse();
    }

    @Test
    @Order(1)
    void testSaveModalDownloadHtml() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#saveMsgs").click();
        Download download = page.waitForDownload(() -> page.frameLocator("#rbellog-details-iframe").locator("#saveHtmlBtn").click());
        // wait for download to complete
        sleep(1000);
        assertAll(
            () -> assertThat(download.page().locator("#test-tiger-logo").isVisible()).isTrue(),
            () -> assertThat(
                download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card").count()).isPositive(),
            () ->assertThat(download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-header")
                .count()).isPositive(),
            () ->assertThat(download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-content")
                .count()).isPositive()
        );
     }

    @Test
    @Order(2)
    void testSaveModalDownloadTgr() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#saveMsgs").click();
        Download download = page.waitForDownload(() -> page.frameLocator("#rbellog-details-iframe").locator("#saveTrafficBtn").click());
        // wait for download to complete
        sleep(1000);
        assertAll(
            () -> assertThat(download.page().locator("#test-tiger-logo").isVisible()).isTrue(),
            () -> assertThat(
                download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card").count()).isPositive(),
            () ->assertThat(download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-header")
                .count()).isPositive(),
            () ->assertThat(download.page().frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-content")
                .count()).isPositive()
        );
     }

    @Test
    @Order(5)
    void testPageButton() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo("1");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").count()).isEqualTo(4);
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").last().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number")));
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo("61");
    }

    @Test
    @Order(4)
    void testSizeButton() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("20");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").count()).isEqualTo(4);
        page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").last().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number")));
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("64");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
        page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").nth(1).click();
    }
}
