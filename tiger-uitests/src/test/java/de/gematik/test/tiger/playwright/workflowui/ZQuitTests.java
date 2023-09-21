/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.microsoft.playwright.Page;
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
    void testClickOnLastRequestChangesPageNumberInRbelLogDetails() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").first().click();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
        page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").nth(1).click();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").first().click();
        String number1 = (String) page.evaluate("document.getElementsByClassName('test-rbel-link')[0].textContent");
        String number2 = (String) page.evaluate("document.getElementsByClassName('test-rbel-link')[25].textContent");

        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first()));
        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo(number1),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isNotEqualTo(number2)
        );
        String pageNo = page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").last().click();
        // somehow I need to wait
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay")));
        String pageNo2 = page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
        int value = Integer.parseInt(number2)+1;
        assertAll(
            () -> assertThat(pageNo).isNotEqualTo(pageNo2),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isNotEqualTo(number1),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo(String.valueOf(value))
        );
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").first().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("20"));
    }

    @Test
    void testAFilterModal() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filterModalDialog").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").textContent()).isEmpty(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent()).contains("no request"),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent()).contains("no request"),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("Filter didn't match any of the 52 messages.")
        );
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filterModalDialog").isVisible()).isFalse();
    }

    @Test
    void testAFilterModalResetFilter() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.body == \"hello=world\"");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe")
                .locator("#filteredMessage").textContent()).isEqualTo("4 of 52 did match the filter criteria."));
        String filteredMessage = page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
        int count = page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-msg-body-content").count();
        page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe")
                .locator("#filteredMessage").textContent()).isEqualTo("Filter didn't match any of the 52 messages."));
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("");
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
        assertThat(filteredMessage).isEqualTo("4 of 52 did match the filter criteria.");
        assertThat(count).isEqualTo(3);
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("Filter didn't match any of the 52 messages.");
    }

    @Test
    void testAFilterModalSetSenderFilter() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.sender == \"put\"");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent")));
        String requestToContent = page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent();
        String requestFromContent = page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent();
        String filteredMessage = page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent();
        page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
        assertAll(
            () -> assertThat(requestToContent).contains("no request"),
            () -> assertThat(requestFromContent).contains("no request"),
            () -> assertThat(filteredMessage).isEqualTo("0 of 52 did match the filter criteria.")
        );
    }

    @Test
    void testXResetButton() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();

        Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(externalPage.locator("#rbelmsglist .test-card")));
        assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive();
        externalPage.locator("#resetMsgs").click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isZero());
        assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isZero();
        externalPage.close();
    }

    @Test
    void testZQuitButton() {
        page.querySelector("#test-tiger-logo").click();
        page.querySelector("#test-sidebar-quit-icon").click();
        assertAll(
            () -> assertThat(page.querySelector("#sidebar-left.test-sidebar-quit").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#workflow-messages.test-messages-quit").isVisible()).isTrue()
        );

        page.screenshot(new Page.ScreenshotOptions().setFullPage(false).setPath(getPath("workflowui_quit.png")));
    }

    @Test
    void testPageButton() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").first().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("20"));

        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo("1");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").count()).isEqualTo(3);
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").last().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo("41"));
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo("41");
    }

    @Test
    void testSizeButton() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").click();
        page.frameLocator("#rbellog-details-iframe").locator("#pageSelector .dropdown-item").first().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("20"));

        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("20");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").count()).isEqualTo(4);
        page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").last().click();
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("52"));
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo("52");
        page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").click();
        page.frameLocator("#rbellog-details-iframe").locator("#sizeSelector .dropdown-item").nth(1).click();
    }
}
