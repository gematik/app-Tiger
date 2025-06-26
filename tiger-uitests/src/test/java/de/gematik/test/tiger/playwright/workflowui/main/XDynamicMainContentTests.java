/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.playwright.workflowui.main;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for dynamic content of main content area, e.g. server log pane. */
class XDynamicMainContentTests extends AbstractBase {

  @Test
  void testServerLogPaneActive() {
    page.querySelector("#test-server-log-tab").click();
    assertAll(
        () -> assertThat(page.locator("#test-execution-pane-tab.active")).not().isVisible(),
        () -> assertThat(page.locator("#test-server-log-tab.active")).isVisible(),
        () -> assertThat(page.locator("#test-server-log-pane-input-text")).isVisible(),
        () -> assertThat(page.locator("#test-server-log-pane-select")).isVisible());
  }

  @ParameterizedTest
  @ValueSource(strings = {"localTigerProxy", "remoteTigerProxy", "httpbin"})
  void testServerLogsLogsOfServerShown(String server) {
    page.querySelector("#test-server-log-tab").click();
    page.locator("#test-server-log-pane-select").selectOption("5");
    // as the server buttons are toggling, we need to reset all previous activated ones by selecting
    // the "Show all logs" button, then we can select a single server
    page.querySelector("#test-server-log-pane-server-all").click();
    page.querySelector("#test-server-log-pane-server-" + server).click();
    page.locator(".test-server-log-pane-log-1")
        .all()
        .forEach(log -> assertThat(log).containsText(server));
  }

  @Test
  void testServerLogNoLogsOfHttpbinShown() {
    page.querySelector("#test-server-log-tab").click();
    page.querySelector("#test-server-log-pane-server-localTigerProxy").click();
    page.querySelector("#test-server-log-pane-server-remoteTigerProxy").click();
    page.locator("#test-server-log-pane-select").selectOption("5");
    page.locator(".test-server-log-pane-log-1")
        .all()
        .forEach(log -> assertThat(log).not().containsText("httpbin"));
  }

  @Test
  void testServerLogLogsShownOnInfoLevel() {
    page.querySelector("#test-server-log-tab").click();
    page.querySelector("#test-server-log-pane-server-all").click();
    page.locator("#test-server-log-pane-select").selectOption("2");
    page.locator("#test-server-log-pane-input-text").fill("started");
    assertThat(page.locator(".test-server-log-pane-log-1")).hasCount(5);
    page.locator("#test-server-log-pane-input-text").fill("");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(page.locator("#test-server-log-pane-input-text")).isEmpty());
  }

  @Test
  void testServerLogNoLogsShown() {
    page.querySelector("#test-server-log-tab").click();
    page.querySelector("#test-server-log-pane-server-all").click();
    page.locator("#test-server-log-pane-select").selectOption("5");
    page.locator("#test-server-log-pane-input-text").fill("ready");
    assertThat(page.locator(".test-server-log-pane-log-1")).not().isVisible();
    page.locator("#test-server-log-pane-input-text").fill("");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(page.locator("#test-server-log-pane-input-text")).isEmpty());
  }

  @Test
  void testServerLogTwoLogsShown() {
    page.querySelector("#test-server-log-tab").click();
    page.querySelector("#test-server-log-pane-server-all").click();
    page.locator("#test-server-log-pane-input-text").fill("");
    page.locator("#test-server-log-pane-select").selectOption("5");
    page.locator("#test-server-log-pane-input-text").fill("READY");
    assertThat(page.locator(".test-server-log-pane-log-1")).hasCount(2);
    page.locator("#test-server-log-pane-input-text").fill("");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(page.locator("#test-server-log-pane-input-text")).isEmpty());
  }

  @Test
  void testClickOnRequestOpensRbelLogDetails() {
    page.querySelector("#test-execution-pane-tab").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
    page.locator(".test-rbel-link").first().click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
  }

  @Test
  void testServerLogNoLogsShownOnErrorLevel() {
    page.querySelector("#test-server-log-tab").click();
    page.querySelector("#test-server-log-pane-server-all").click();
    page.locator("#test-server-log-pane-select").selectOption("0");
    assertThat(page.locator(".test-server-log-pane-log-1")).not().isVisible();
    page.locator("#test-server-log-pane-input-text").fill("");
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(page.locator("#test-server-log-pane-input-text")).isEmpty());
  }
}
