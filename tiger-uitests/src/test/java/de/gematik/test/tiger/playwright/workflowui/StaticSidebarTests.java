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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/** Static tests for the sidebar of the workflow ui. */
@Slf4j
class StaticSidebarTests extends AbstractTests {

  @Test
  void testSidebarClosedIconsAreVisible() {
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-title")).not().isVisible(),
        () -> assertThat(page.locator("div.test-sidebar-collapsed")).isVisible(),
        () -> assertThat(page.locator("#test-tiger-logo")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-quit-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-pause-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-feature-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-server-icon")).isVisible());
  }

  @Test
  void testSidebarOpenIconsAreVisible() {
    page.querySelector("#test-tiger-logo").click();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-title")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-feature")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-server")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-version")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-build")).isVisible(),
        () -> assertThat(page.locator("#test-tiger-logo")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-quit-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-pause-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-feature-icon")).isVisible(),
        () -> assertThat(page.locator("#test-sidebar-server-icon")).isVisible());
  }

  @Test
  void testStatus() {
    page.querySelector("#test-tiger-logo").click();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-statusbox")).isVisible(),
        () ->
            assertThat(
                    page.locator("#test-sidebar-statusbox .test-sidebar-status-features"))
                        .isVisible(),
        () ->
            assertThat(
                    page.locator("#test-sidebar-statusbox .test-sidebar-status-scenarios"))
                        .isVisible(),
        () -> assertThat(page.locator("#test-sidebar-status-started")).isVisible());
  }
}
