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
class StaticMainContentTests extends AbstractTests {

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
  void testPassedScenario() {
    page.querySelector("#test-tiger-logo").click();
    await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".scenarioLink")
                            .first()
                            .locator("xpath=..")
                            .locator(".test-passed")
                            .first()
                            .isVisible())
                    .isTrue());
  }

  @Test
  void testExecutingScenario() {
    page.querySelector("#test-tiger-logo").click();
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".test-pending")
                            .first()
                            .isVisible())
                    .isTrue());

    page.locator("#workflow-messages").locator(".btn-success").first().click();
  }

  @Test
  void testFailedScenario() {
    page.querySelector("#test-tiger-logo").click();
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".test-failed")
                            .first()
                            .isVisible())
                    .isTrue());
  }

  @Test
  void testExecutionPaneActive() {
    assertAll(
        () -> assertThat(page.locator("#test-execution-pane-tab.active").isVisible()).isTrue(),
        () -> assertThat(page.locator("#test-server-log-tab.active").isVisible()).isFalse());
  }

  @Test
  void testExecutionPaneDateTime() {
    assertThat(page.locator("#test-execution-pane-date").isVisible()).isTrue();
  }

  @Test
  void testExecutionPaneGematikLogo() {
    assertThat(page.locator("#test-gematik-logo").isVisible()).isTrue();
  }

  @Test
  void testServerLogPaneExists() {
    page.querySelector("#test-server-log-tab").click();
    assertAll(
        () ->
            assertThat(page.locator("#test-server-log-pane-buttons").locator(".active").count())
                .isEqualTo(1),
        () ->
            assertThat(
                    page.locator("#test-server-log-pane-buttons")
                        .locator("#test-server-log-pane-server-all.active")
                        .isVisible())
                .isTrue());
  }

  @Test
  void testServerPanInputTextIsEmpty() {
    page.querySelector("#test-server-log-tab").click();
    assertAll(
        () -> assertThat(page.locator("#test-server-log-pane-input-text").isVisible()).isTrue(),
        () ->
            assertThat(page.locator("#test-server-log-pane-input-text").textContent())
                .isNullOrEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"localTigerProxy", "httpbin", "remoteTigerProxy"})
  void testServerPanServerButtonsExist(String server) {
    page.querySelector("#test-server-log-tab").click();
    assertThat(
            page.locator("#test-server-log-pane-buttons")
                .locator(".test-server-log-pane-server-" + server)
                .isVisible())
        .isFalse();
  }
}
