/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

/**
 * Dynamic tests of the workflow ui, that can only be tested when the feature file has run through at a later time.
 */
class XLaterTests extends AbstractTests {

    @Test
    void testNavbarWithButtonsExists() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertAll(
            () -> assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#webui-navbar").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#dropdown-hide-button").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").isVisible()).isTrue(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#saveMsgs").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-selection").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent()).endsWith("1"),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#pageSizeDisplay").textContent()).endsWith("20"),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#dropdown-page-size").isVisible()).isTrue(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#routeModalBtn").isVisible()).isFalse(),
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#scrollLockBtn").isVisible()).isFalse(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#resetMsgs").isVisible()).isFalse(),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#importMsgs").isVisible()).isFalse()
        );
    }

    @Test
    void testRbelMessagesExists() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        assertAll(
            () -> assertThat(
                page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card").count()).isPositive(),
            () ->assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-header")
                .count()).isPositive(),
            () ->assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-card-content")
                .count()).isPositive()
        );
    }

    @Test
    void testClickOnLastRequestChangesPageNumberInRbelLogDetails() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").first().click();
        String number1 = page.locator(".test-rbel-link").first().textContent();
        String number2 = page.locator(".test-rbel-link").last().textContent();
        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isEqualTo(number1),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isNotEqualTo(number2)
        );
        String pageNo = page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
        page.locator("#test-webui-slider").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").last().click();
        // somehow I need to wait
        Thread.sleep(1000);
        String pageNo2 = page.frameLocator("#rbellog-details-iframe").locator("#pageNumberDisplay").textContent();
        int value = Integer.parseInt(number2)+1;
        assertAll(
            () -> assertThat(pageNo).isNotEqualTo(pageNo2),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first().textContent()).isNotEqualTo(number1),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last().textContent()).isEqualTo(String.valueOf(value))
        );
    }

    @Test
    void testFilterModal() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
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
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("Filter didn\'t match any of the 64 messages.")
        );
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filterModalDialog").isVisible()).isFalse();
    }

    @Test
    void testRoutingModal() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();

        Page externalPage = page.waitForPopup(() -> {
            page.locator("#test-rbel-webui-url").click();
        });
        sleep(1000);
        externalPage.locator("#routeModalBtn").click();
        assertAll(
            () -> assertThat(externalPage.locator("#routeModalDialog").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#routingModalButtonClose").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#addNewRouteBtn").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#addNewRouteFromField").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#addNewRouteFromField").textContent()).isEmpty(),
            () -> assertThat(externalPage.locator("#addNewRouteToField").isVisible()).isTrue(),
            () -> assertThat(externalPage.locator("#addNewRouteToField").textContent()).isEmpty()
        );
        externalPage.locator("#routingModalButtonClose").click();
        assertThat(externalPage.locator("#routeModalDialog").isVisible()).isFalse();
    }

    @Test
    void testFilterModalSetSenderFilter() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.sender == \"put\"");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        sleep(1000);
        assertAll(
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestToContent").textContent()).contains("no request"),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#requestFromContent").textContent()).contains("no request"),
            () -> assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("0 of 64 did match the filter criteria.")
        );
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    }

    @Test
    void testFilterModalResetFilter() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalBtn").click();
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionInput").fill("$.body == \"hello=world\"");
        page.frameLocator("#rbellog-details-iframe").locator("#setFilterCriterionBtn").click();
        sleep(1000);
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("4 of 64 did match the filter criteria.");
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#test-rbel-section .test-msg-body-content").count()).isEqualTo(3);
        page.frameLocator("#rbellog-details-iframe").locator("#resetFilterCriterionBtn").click();
        sleep(1000);
        assertThat(page.frameLocator("#rbellog-details-iframe").locator("#filteredMessage").textContent()).isEqualTo("Filter didn't match any of the 64 messages.");
        page.frameLocator("#rbellog-details-iframe").locator("#filterModalButtonClose").click();
    }


    @Test
    void testFeatures() {
        page.querySelector("#test-tiger-logo").click();
        assertAll(
            () -> assertThat(page.locator("#test-sidebar-featurelistbox").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name").count()).isEqualTo(1),
            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name").count()).isEqualTo(25),
            () -> assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-index").count()).isEqualTo(11)
        );
    }
}
