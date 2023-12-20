/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Dynamic tests for the sidebar of the workflow ui, such as server box, feature box, status box
 * tests.
 */
@Slf4j
class XDynamicSidebarTests extends AbstractTests {

  @Test
  void testSidebarIsClosedWhenClickedOnDoubleArrow() {
    page.querySelector("#test-tiger-logo").click();
    page.querySelector("#test-sidebar-close-icon").click();
    assertAll(
        () -> assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse(),
        () -> assertThat(page.locator("#test-sidebar-statusbox").isVisible()).isFalse(),
        () -> assertThat(page.locator("#test-sidebar-statusbox").isVisible()).isFalse(),
        () -> assertThat(page.locator("#test-sidebar-statusbox").isVisible()).isFalse(),
        () -> assertThat(page.locator("#test-sidebar-status-started").isVisible()).isFalse(),
        () -> assertThat(page.querySelector("#test-sidebar-quit-icon").isVisible()).isTrue(),
        () -> assertThat(page.querySelector("#test-sidebar-pause-icon").isVisible()).isTrue(),
        () -> assertThat(page.querySelector("#test-sidebar-status-icon").isVisible()).isTrue(),
        () -> assertThat(page.querySelector("#test-sidebar-feature-icon").isVisible()).isTrue(),
        () -> assertThat(page.querySelector("#test-sidebar-server-icon").isVisible()).isTrue());
  }

  @Test
  void testFeatureBoxClickOnLastScenario() {
    page.querySelector("#test-tiger-logo").click();
    page.locator(".test-sidebar-scenario-name").last().locator(".scenarioLink").click();
    String sidebarTitle = page.locator(".test-sidebar-scenario-name").last().getAttribute("title");
    String featureTitle = page.locator(".test-execution-pane-scenario-title").last().textContent();
    assertAll(() -> assertThat(featureTitle.trim()).startsWith(sidebarTitle));
  }

  @Test
  void testPassedStepInFeatureBoxAndInExecutionPane() {
    page.querySelector("#test-tiger-logo").click();
    assertAll(
        () -> assertThat(page.locator("#sidebar-left .test-passed").first().isVisible()).isTrue(),
        () ->
            assertThat(page.locator("#execution_table .test-passed").first().isVisible()).isTrue());
  }

  @Test
  void testFindFailedStepInFeatureBoxAndInExecutionPane() {
    page.querySelector("#test-tiger-logo").click();
    String sidebarTitle =
        page.locator("#sidebar-left .test-failed").first().locator("..").textContent();
    String featureTitle =
        page.locator(".test-execution-pane-scenario-title .test-failed")
            .first()
            .locator("..")
            .textContent()
            .trim();
    assertThat(sidebarTitle).containsSequence(featureTitle.trim());
    assertThat(page.locator(".test-step-status-skipped").first().locator("..").textContent())
        .isEqualTo(
            "And TGR assert \"!{rbel:currentRequestAsString('$.path')}\" matches"
                + " \"\\/not_a_file\\/?\"");
  }

  @Test
  void ServerBoxAllServerRunning() {
    page.querySelector("#test-tiger-logo").click();
    List<Locator> servers =
        page.locator("#test-sidebar-server-status-box .test-sidebar-server-status").all();
    assertThat(servers).hasSize(3);
    servers.forEach(server -> assertThat(server.textContent()).contains("RUNNING"));
    page.locator("#test-sidebar-server-status .test-sidebar-server-name")
        .all()
        .forEach(
            server ->
                assertThat(
                    server.textContent().equals("local_tiger_proxy")
                        || server.textContent().equals("httpbin")
                        || server.textContent().equals("remoteTigerProxy")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void ServerBoxTigerProxyWebUiStarted(int counter) {
    page.querySelector("#test-tiger-logo").click();
    Page page1 =
        page.waitForPopup(
            () -> page.locator("#sidebar-left .test-sidebar-server-url-icon").nth(counter).click());
    await()
        .atMost(1000, TimeUnit.MILLISECONDS)
        .until(() -> page1.locator("#test-tiger-logo").isVisible());
    assertThat(page1.locator("#test-tiger-logo").isVisible()).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                          | 0 |
        remoteTigerProxy  | 1 |
        httpbin           | 2 |
    """)
  void ServerBoxLocalTigerProxyLogfiles(String servername, int counter) {
    page.querySelector("#test-tiger-logo").click();
    page.locator("#sidebar-left .test-sidebar-server-log-icon").nth(counter).click();
    assertAll(
        () ->
            assertThat(page.locator(".test-sidebar-server-logs").nth(counter).isVisible()).isTrue(),
        () ->
            assertThat(
                    page.locator(".test-sidebar-server-logs")
                        .nth(counter)
                        .locator(".test-sidebar-server-log")
                        .first()
                        .isVisible())
                .isTrue(),
        () ->
            assertThat(
                    page.locator(".test-sidebar-server-logs")
                        .nth(counter)
                        .locator(".test-sidebar-server-log")
                        .last()
                        .isVisible())
                .isTrue());
    if (servername != null) {
      await()
          .atMost(10, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertThat(
                          page.locator(".test-sidebar-server-logs")
                              .nth(counter)
                              .locator(".test-sidebar-server-log")
                              .last()
                              .textContent())
                      .contains(servername + " READY"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "#test-tiger-logo",
        "#test-sidebar-status-icon",
        "#test-sidebar-feature-icon",
        "#test-sidebar-server-icon"
      })
  void testSidebarIsClosedAndOpensOnIconClickAndClosesAgain(String iconSelector) {
    page.querySelector(iconSelector).click();
    assertThat(page.querySelector("#test-sidebar-title").isVisible()).isTrue();
    page.querySelector(iconSelector).click();
    assertThat(page.querySelector("#test-sidebar-title").isVisible()).isFalse();
  }

  @Test
  void testExecutionPaneScenariosExists() {
    assertAll(
        () -> assertThat(page.locator(".test-execution-pane-feature-title").count()).isEqualTo(2),
        () -> assertThat(page.locator(".test-execution-pane-scenario-title").count()).isPositive(),
        () ->
            assertThat(
                    page.locator(".test-execution-pane-feature-title")
                        .locator("..")
                        .locator(".test-failed")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator("..")
                        .locator(".test-skipped")
                        .count())
                .isPositive(),
        () ->
            assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator("..")
                        .locator(".test-passed")
                        .count())
                .isPositive());
  }
}
