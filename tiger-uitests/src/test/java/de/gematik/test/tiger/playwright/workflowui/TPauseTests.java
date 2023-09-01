/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for test the pause button.
 */
class TPauseTests extends AbstractTests {
    @Test
    void testPauseButton() {
        page.querySelector("#test-tiger-logo").click();
        page.querySelector("#test-sidebar-pause-icon").click();
        assertAll(
            () -> assertThat(page.locator("#sidebar-left.test-sidebar-paused").isVisible()).isTrue()
            // cannot check pending because tests when through or have not startet jet
            //() -> assertThat(page.locator("#execution_table .test-status-pending").isVisible()).isTrue(),
            //() -> assertThat(page.locator("#execution_table .test-pending").isVisible()).isTrue()
        );
    }

    @AfterEach
    void unpause() {
        page.querySelector("#test-sidebar-pause-icon").click();
    }
}
