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
import org.junit.jupiter.api.Test;

/**
 * Test class for testing the unstarted test statically.
 */
class StaticPlayFunctionalityTest extends AbstractBase {


    @Test
    void testNoPassedStepsInFeatureBoxAndInExecutionPane() {
        page.querySelector("#test-tiger-logo").click();
        assertAll(
            () -> assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(5),
            () -> assertThat(page.locator("#sidebar-left .test-passed")).hasCount(0),
            () -> assertThat(page.locator("#execution_table .test-passed")).hasCount(0));
    }

    @Test
    void testExecutionPaneUnstartedScenariosExists() {
        page.querySelector("#test-execution-pane-tab").click();
        assertAll(
            () -> assertThat(page.locator(".test-execution-pane-feature-title")).hasCount(1),
            () -> assertThat(page.locator(".test-execution-pane-feature-title").locator(".test-feature-status-word")).hasText("TEST_DISCOVERED"),
            () -> assertThat(page.locator(".test-execution-pane-scenario-title")).hasCount(5),
            () ->
                assertThat(
                    page.locator(".test-execution-pane-feature-title")
                        .locator(".test-status-failed")
                ).hasCount(0),
            () ->
                assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator(".test-status-skipped")
                ).hasCount(0),
            () ->
                assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator(".test-status-test_discovered")
                ).hasCount(5),
            () ->
                assertThat(
                    page.locator(".test-execution-pane-scenario-title")
                        .locator(".test-status-passed")
                ).hasCount(0));
    }

    @Test
    void testTooltipLargeRunButtonExists() {
        page.querySelector("#test-execution-pane-tab").click();
        page.querySelector("#test-tiger-logo").click();

        assertAll(
            () -> assertThat(page.locator(".test-play-button").first()).hasAttribute("title", "Run Scenario"),
            () -> assertThat(page.locator(".test-play-small-button").first()).hasAttribute("title", "Run Scenario"));
    }
}
