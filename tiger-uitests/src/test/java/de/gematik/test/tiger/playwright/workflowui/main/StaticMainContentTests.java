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

import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tests for the static content of the main window, that means execution pane and server log pane.
 */
@TestMethodOrder(OrderAnnotation.class)
class StaticMainContentTests extends AbstractBase {

  @Test
  void testPassedScenario() {
    openSidebar();
    await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".scenarioLink")
                            .first()
                            .locator("xpath=..")
                            .locator(".test-passed")
                            .first())
                    .isVisible());
  }

  @Test
  void testExecutingScenario() {
    openSidebar();
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".test-pending")
                            .first())
                    .isVisible());

    page.locator("#workflow-messages").locator(".btn-banner-close").first().click();
  }

  @Test
  void testFailedScenario() {
    openSidebar();
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        page.locator("#test-sidebar-featurelistbox")
                            .locator(".test-failed")
                            .first())
                    .isVisible());
  }
}
