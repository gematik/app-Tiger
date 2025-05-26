/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.gematik.test.tiger.playwright.workflowui.main;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Dynamic tests for the sidebar of the workflow ui, such as server box, feature box, status box
 * tests.
 */
@Slf4j
class XDynamicSidebarTests extends AbstractBase {

  @Test
  void testSidebarIsClosedWhenClickedOnDoubleArrow() {
    openSidebar();
    page.querySelector("#test-sidebar-close-icon").click();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-title")).not().isVisible(),
        () -> assertThat(page.locator("#test-sidebar-statusbox")).not().isVisible(),
        () -> assertThat(page.locator("#test-sidebar-statusbox")).not().isVisible(),
        () -> assertThat(page.locator("#test-sidebar-statusbox")).not().isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status-started")).not().isVisible(),
        () -> assertThat(page.locator("#test-sidebar-quit-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-pause-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-feature-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-server-icon")).isVisible());
  }

  @Test
  void testFeatureBoxClickOnLastScenario() {
    openSidebar();
    page.locator(".test-sidebar-scenario-name").last().locator(".scenarioLink").click();
    String sidebarTitle = page.locator(".test-sidebar-scenario-name").last().getAttribute("title");
    String featureTitle = page.locator(".test-execution-pane-scenario-title").last().textContent();
    assertAll(() -> Assertions.assertThat(featureTitle.trim()).startsWith(sidebarTitle));
  }

  @Test
  void testPassedStepInFeatureBoxAndInExecutionPane() {
    openSidebar();
    assertAll(
        () -> assertThat(page.locator("#sidebar-left .test-passed").first()).isVisible(),
        () -> assertThat(page.locator("#execution_table .test-passed").first()).isVisible());
  }

  @Test
  void testFindFailedStepInFeatureBoxAndInExecutionPane() {
    openSidebar();
    Locator sidebarTitle = page.locator("#sidebar-left .test-failed").first().locator("..");
    String featureTitle =
        page.locator(".test-execution-pane-scenario-title .test-failed")
            .first()
            .locator("..")
            .textContent()
            .trim();
    assertThat(sidebarTitle).containsText(featureTitle.trim());
    assertThat(page.locator(".test-step-status-skipped").first().locator(".."))
        .containsText(
            "And TGR assert \"!{rbel:currentRequestAsString('$.path')}\" matches \"/not_a_file\"");
  }

  @Test
  void ServerBoxAllServerRunning() {
    openSidebar();
    List<Locator> servers =
        page.locator("#test-sidebar-server-status-box .test-sidebar-server-status").all();
    Assertions.assertThat(servers).hasSize(3);
    servers.forEach(server -> assertThat(server).containsText("RUNNING"));
    for (Locator server :
        page.locator("#test-sidebar-server-status .test-sidebar-server-name").all()) {
      assertThat(server)
          .containsText(new String[] {"local_tiger_proxy", "httpbin", "remoteTigerProxy"});
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void ServerBoxTigerProxyWebUiStarted(int counter) {
    openSidebar();
    Page page1 =
        page.waitForPopup(
            () -> page.locator("#sidebar-left .test-sidebar-server-url-icon").nth(counter).click());
    await().atMost(10, TimeUnit.SECONDS).until(() -> page1.locator("#test-tiger-logo").isVisible());
    assertThat(page1.locator("#test-tiger-logo")).isVisible();
    page1.close();
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
    openSidebar();
    log.info(
        "click on "
            + page.locator("#sidebar-left .test-sidebar-server-log-icon")
                .nth(counter)
                .locator("..")
                .textContent());
    page.locator("#sidebar-left .test-sidebar-server-log-icon").nth(counter).click();
    assertAll(
        () -> assertThat(page.locator(".test-sidebar-server-logs").nth(counter)).isVisible(),
        () ->
            assertThat(
                    page.locator(".test-sidebar-server-logs")
                        .nth(counter)
                        .locator(".test-sidebar-server-log")
                        .first())
                .isVisible(),
        () ->
            assertThat(
                    page.locator(".test-sidebar-server-logs")
                        .nth(counter)
                        .locator(".test-sidebar-server-log")
                        .last())
                .isVisible());
    log.info("Servername: {}", servername);

    if (servername != null) {
      await()
          .atMost(30, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertThat(
                          page.locator(".test-sidebar-server-logs")
                              .nth(counter)
                              .locator(".test-sidebar-server-log")
                              .last())
                      .containsText(
                          Pattern.compile("^(remoteTigerProxy|httpbin) (READY|started)")));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"#test-tiger-logo"})
  void testSidebarIsClosedAndOpensOnIconClickAndClosesAgain(String iconSelector) {
    page.querySelector(iconSelector).click();
    assertThat(page.locator("#test-sidebar-title")).isVisible();
    page.querySelector(iconSelector).click();
    assertThat(page.locator("#test-sidebar-title")).not().isVisible();
  }

  @Test
  void testExecutionPaneScenariosExists() {
    // wait for up to 30 seconds for the other thread to finish execution of second feature file
    await()
        .atMost(120, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(() -> page.locator(".test-execution-pane-feature-title").count() == 2);
    assertAll(
        () -> assertThat(page.locator(".test-execution-pane-feature-title")).hasCount(2),
        () ->
            Assertions.assertThat(page.locator(".test-execution-pane-scenario-title").count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    page.locator(".test-execution-pane-feature-title")
                        .locator("..")
                        .locator(".test-failed")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator("..")
                        .locator(".test-skipped")
                        .count())
                .isPositive(),
        () ->
            Assertions.assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator("..")
                        .locator(".test-passed")
                        .count())
                .isPositive());
  }
}
