/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import org.junit.jupiter.api.Test;

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

    @Test
    void testClickOnRequestOpensRbelLogDetails() {
        page.querySelector("#test-execution-pane-tab").click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isFalse();
        page.locator(".test-rbel-link").first().click();
        assertThat(page.locator("#rbellog_details_pane").isVisible()).isTrue();
    }

}
