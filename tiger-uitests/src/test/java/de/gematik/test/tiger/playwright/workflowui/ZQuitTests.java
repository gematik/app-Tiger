/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * These tests should run at the very last because the testQuitButton() quits the tiger/workflowui.
 */
@Slf4j
class ZQuitTests extends AbstractTests {

    @Test
    void testResetButton() throws InterruptedException {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator("#test-webui-slider").click();

        Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
        sleep(1000);
        assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isPositive();
        externalPage.locator("#resetMsgs").click();
        assertThat(externalPage.locator("#rbelmsglist .test-card").count()).isZero();
    }

    @Test
    void testQuitButton() {
        page.querySelector("#test-tiger-logo").click();
        page.querySelector("#test-sidebar-quit-icon").click();
        assertAll(
            () -> assertThat(page.querySelector("#sidebar-left.test-sidebar-quit").isVisible()).isTrue(),
            () -> assertThat(page.querySelector("#workflow-messages.test-messages-quit").isVisible()).isTrue()
        );
    }
}
