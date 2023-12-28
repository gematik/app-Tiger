/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

/** Tests for static content of the web ui content, e.g. rbel logo. */
class StaticRbelLogTests extends AbstractTests {

  @Test
  void testExecutionPaneRbelLogo() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    await().untilAsserted(() -> assertThat(page.locator("#test-rbel-logo").isVisible()).isTrue());
    screenshot(page, "maincontent_rbelpath.png");
    screenshot(page, "maincontent_rbelpath_urllink_highlight.png", "test-rbel-webui-url", true);
  }
}
