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
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the static content of the main window, that means execution pane and server log pane.
 */
@TestMethodOrder(OrderAnnotation.class)
class StaticMainContentTest extends AbstractBase {

  @Test
  @Order(1)
  void testServerLogPaneButtonsExists() {
    page.querySelector("#test-server-log-tab").click();
    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> page.locator("#test-server-log-pane-server-httpbin").isVisible());
    assertAll(
        () ->
            PlaywrightAssertions.assertThat(
                    page.locator("#test-server-log-pane-server-localTigerProxy"))
                .isVisible(),
        () ->
            PlaywrightAssertions.assertThat(
                    page.locator("#test-server-log-pane-server-remoteTigerProxy"))
                .isVisible(),
        () ->
            PlaywrightAssertions.assertThat(page.locator("#test-server-log-pane-server-httpbin"))
                .isVisible(),
        () ->
            PlaywrightAssertions.assertThat(page.locator(".test-server-log-pane-log-row").first())
                .isAttached(),
        () ->
            PlaywrightAssertions.assertThat(page.locator(".test-server-log-pane-log-1").first())
                .isAttached(),
        () ->
            PlaywrightAssertions.assertThat(page.locator(".test-server-log-pane-log-2").first())
                .isAttached(),
        () ->
            PlaywrightAssertions.assertThat(page.locator(".test-server-log-pane-log-3").first())
                .isAttached(),
        () ->
            assertThat(
                    page.evaluate(
                        "document.getElementById('test-server-log-pane-select').options[document.getElementById('test-server-log-pane-select').selectedIndex].text"))
                .isEqualTo("ALL"));
  }

  @Test
  void testExecutionPaneActive() {
    assertAll(
        () -> PlaywrightAssertions.assertThat(page.locator("#test-execution-pane-tab.active")).isVisible(),
        () -> PlaywrightAssertions.assertThat(page.locator("#test-server-log-tab.active")).not().isVisible());
  }

  @Test
  void testExecutionPaneDateTime() {
    PlaywrightAssertions.assertThat(page.locator("#test-execution-pane-date")).isVisible();
  }

  @Test
  void testExecutionPaneGematikLogo() {
    PlaywrightAssertions.assertThat(page.locator("#test-gematik-logo")).isVisible();
  }

  @Test
  void testServerLogPaneExists() {
    page.querySelector("#test-server-log-tab").click();
    assertAll(
        () ->
            assertThat(page.locator("#test-server-log-pane-buttons").locator(".active").count())
                .isEqualTo(1),
        () ->
            PlaywrightAssertions.assertThat(
                    page.locator("#test-server-log-pane-buttons")
                        .locator("#test-server-log-pane-server-all.active"))
                        .isVisible());
  }

  @Test
  void testServerPanInputTextIsEmpty() {
    page.querySelector("#test-server-log-tab").click();
    assertAll(
        () -> PlaywrightAssertions.assertThat(page.locator("#test-server-log-pane-input-text")).isVisible(),
        () ->
            PlaywrightAssertions.assertThat(page.locator("#test-server-log-pane-input-text")).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"localTigerProxy", "httpbin", "remoteTigerProxy"})
  void testServerPanServerButtonsExist(String server) {
    page.querySelector("#test-server-log-tab").click();
    PlaywrightAssertions.assertThat(
            page.locator("#test-server-log-pane-buttons")
                .locator(".test-server-log-pane-server-" + server))
                .not().isVisible();
  }
}
