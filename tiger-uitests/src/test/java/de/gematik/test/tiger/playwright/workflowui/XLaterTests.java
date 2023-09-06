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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * Dynamic tests of the workflow ui, that can only be tested when the feature file has run through at a later time.
 */
@TestMethodOrder(OrderAnnotation.class)
class XLaterTests extends AbstractTests {

    @Test
    @Order(7)
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
    @Order(1)
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
    @Order(6)
    void testRoutingModal() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();

        Page externalPage = page.waitForPopup(() -> {
            page.locator("#test-rbel-webui-url").click();
        });
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertNotNull(externalPage.locator("#routeModalBtn")));
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
    @Order(9)
    void testServers() {
        page.querySelector("#test-tiger-logo").click();
        assertAll(
            () -> assertThat(page.locator("#test-sidebar-server-status-box").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-serverbox").count()).isEqualTo(3),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-name").count()).isEqualTo(3),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-status").count()).isEqualTo(3),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-url").count()).isEqualTo(2),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-url-icon").count()).isEqualTo(2),
            () -> assertThat(page.locator("#test-sidebar-server-status-box .test-sidebar-server-log-icon").count()).isEqualTo(3)
        );
    }

    @BeforeEach
    void printInfoStarted(TestInfo testInfo) {
        System.out.println("started = " + testInfo.getDisplayName());
    }
    @AfterEach
    void printInfoFinished(TestInfo testInfo) {
        System.out.println("finished = " + testInfo.getDisplayName());
    }
}
