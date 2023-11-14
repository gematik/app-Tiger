/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for static content of the web ui content, e.g. rbel logo. */
class StaticRbelLogTests extends AbstractTests {

  @Test
  void testExecutionPaneRbelLogo() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#test-rbel-logo").isVisible()).isTrue();
  }
}
