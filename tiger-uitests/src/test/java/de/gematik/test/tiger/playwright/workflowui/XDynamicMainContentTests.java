/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for dynamic content of main content area, e.g. server log pane.
 */
class XDynamicMainContentTests extends AbstractTests {

    @Test
    void testServerLogPaneActive() {
        page.querySelector("#test-server-log-tab").click();
        assertAll(
            () -> assertThat(page.locator("#test-execution-pane-tab.active").isVisible()).isFalse(),
            () -> assertThat(page.locator("#test-server-log-tab.active").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-server-log-pane-input-text").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-server-log-pane-select").isVisible()).isTrue()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"localTigerProxy", "remoteTigerProxy", "httpbin"})
    void testServerLogsLogsOfServerShown(String server) {
        page.querySelector("#test-server-log-tab").click();
        page.locator("#test-server-log-pane-select").selectOption("5");
        page.querySelector("#test-server-log-pane-server-" + server).click();
        page.locator(".test-server-log-pane-log-1").all()
            .forEach(log -> assertThat(log.textContent().equals(server)));
    }

    @Test
    void testServerLogNoLogsOfHttpbinShown() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-localTigerProxy").click();
        page.querySelector("#test-server-log-pane-server-remoteTigerProxy").click();
        page.locator("#test-server-log-pane-select").selectOption("5");
        page.locator(".test-server-log-pane-log-1").all()
            .forEach(log -> assertThat(log.textContent()).isNotEqualTo("httpbin"));
    }
    @Test
    void testServerLogLogsShownOnInfoLevel() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-all").click();
        page.locator("#test-server-log-pane-select").selectOption("2");
        page.locator("#test-server-log-pane-input-text").type("started");
        assertThat(page.locator(".test-server-log-pane-log-1").all()).hasSize(5);
        page.locator("#test-server-log-pane-input-text").fill("");
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(page.locator("#test-server-log-pane-input-text").textContent()).isEmpty());
    }

    @Test
    void testServerLogNoLogsShown() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-all").click();
        page.locator("#test-server-log-pane-select").selectOption("5");
        page.locator("#test-server-log-pane-input-text").type("ready");
        assertThat(page.locator(".test-server-log-pane-log-1").isVisible()).isFalse();
        page.locator("#test-server-log-pane-input-text").fill("");
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(page.locator("#test-server-log-pane-input-text").textContent()).isEmpty());
    }

    @Test
    void testServerLogTwoLogsShown() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-all").click();
        page.locator("#test-server-log-pane-input-text").fill("");
        page.locator("#test-server-log-pane-select").selectOption("5");
        page.locator("#test-server-log-pane-input-text").type("READY");
        assertThat(page.locator(".test-server-log-pane-log-1").all()).hasSize(2);
        page.locator("#test-server-log-pane-input-text").fill("");
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(page.locator("#test-server-log-pane-input-text").textContent()).isEmpty());
    }


    @Test
    void testClickOnRequestOpensRbelLogDetails() {
        page.querySelector("#test-execution-pane-tab").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").first().click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
    }

    @Test
    void testServerLogNoLogsShownOnErrorLevel() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-all").click();
        page.locator("#test-server-log-pane-select").selectOption("0");
        assertThat(page.locator(".test-server-log-pane-log-1").isVisible()).isFalse();
        page.locator("#test-server-log-pane-input-text").fill("");
        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(page.locator("#test-server-log-pane-input-text").textContent()).isEmpty());
    }
}
