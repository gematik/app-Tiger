/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Static tests for the sidebar of the workflow ui.
 */
@Slf4j
class StaticSidebarTests extends AbstractTests {

    @Test
    void testSidebarClosedIconsAreVisible() {
        assertAll(
            () -> assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse(),
            () -> assertThat(page.querySelector("div.test-sidebar-collapsed").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-tiger-logo").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-quit-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-pause-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-status-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-feature-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-server-icon").isVisible()).isTrue()
        );
    }

    @Test
    void testSidebarOpenIconsAreVisible() {
        page.querySelector("#test-tiger-logo").click();
        assertAll(
            () -> assertThat(page.querySelector("#test-sidebar-title").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-status").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-feature").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-server").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-version").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-build").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-tiger-logo").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-quit-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-pause-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-status-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-feature-icon").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#test-sidebar-server-icon").isVisible()).isTrue()
        );
    }

    @Test
    void testStatus() {
        page.querySelector("#test-tiger-logo").click();
        assertAll(
            () -> assertThat(page.locator("#test-sidebar-statusbox").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-sidebar-statusbox .test-sidebar-status-features").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-sidebar-statusbox .test-sidebar-status-scenarios").isVisible()).isTrue(),
            () -> assertThat(page.locator("#test-sidebar-status-started").isVisible()).isTrue()
        );
    }
}
