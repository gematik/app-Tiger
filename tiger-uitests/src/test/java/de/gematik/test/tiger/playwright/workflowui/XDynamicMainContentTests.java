/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
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
        page.querySelector("#test-server-log-pane-server-" + server).click();
        page.locator(".test-server-log-pane-log-1").all()
            .forEach(log -> assertThat(log.textContent().equals(server)));
    }

    @Test
    void testServerLogNoLogsOfHttpbinShown() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-localTigerProxy").click();
        page.querySelector("#test-server-log-pane-server-remoteTigerProxy").click();
        page.locator(".test-server-log-pane-log-1").all()
            .forEach(log -> assertThat(log.textContent()).isNotEqualTo("httpbin"));
    }

    @Test
    void testServerLogNoLogsShown() {
        page.querySelector("#test-server-log-tab").click();
        page.querySelector("#test-server-log-pane-server-all").click();
        page.locator("#test-server-log-pane-input-text").type("ready");
        assertThat(page.locator(".test-server-log-pane-log-1").isVisible()).isFalse();
        page.locator("#test-server-log-pane-input-text").type("");
    }

//    @Test
//    void testServerLogTwoLogsShown() {
//        page.querySelector("#test-server-log-tab").click();
//        page.querySelector("#test-server-log-pane-server-all").click();
//        page.locator("#test-server-log-pane-input-text").type("READY");
//        assertThat(page.locator(".test-server-log-pane-log-1").all().size()).isEqualTo(2);
//        page.locator("#test-server-log-pane-input-text").type("");
//    }


    @Test
    void testClickOnRequestOpensRbelLogDetails() {
        page.querySelector("#test-execution-pane-tab").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").first().click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
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
