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

package de.gematik.test.tiger.playwright.workflowui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Dynamic tests for the sidebar of the workflow ui for unstarted tests.
 */
@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class XDynamicPlaySidebarTest extends AbstractBase {

    @SuppressWarnings("squid:S2699")
    @Test
    void testAScreenshotSidebar() {
        page.querySelector("#test-execution-pane-tab").click();
        page.querySelector("#test-tiger-logo").click();
        page.evaluate(
            "document.getElementsByClassName(\"test-play-small-button\")[0].style.backgroundColor='yellow'");
        page.evaluate(
            "document.getElementsByClassName(\"test-play-button\")[0].style.backgroundColor='yellow'");

        screenshot(page, "play_button.png");
        page.evaluate(
            "document.getElementsByClassName(\"test-play-small-button\")[0].style.removeProperty(\"background-color\")");
        page.evaluate(
            "document.getElementsByClassName(\"test-play-button\")[0].style.removeProperty(\"background-color\")");
    }

    @Test
    void testRunAScenarioThenAbortInSidebar() {
        page.querySelector("#test-tiger-logo").click();
        page.locator(".test-play-small-button").first().click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(page.querySelector("#test-no-cancel")));
        page.querySelector("#test-no-cancel").click();
        assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(5);
        page.querySelector("#test-execution-pane-tab").click();
        assertThat(
            page.locator(".test-execution-pane-scenario-title")
                .locator(".test-status-test_discovered")
        ).hasCount(5);
    }

    @Test
    void testRunAScenarioThenCommitInASidebar() {
        page.querySelector("#test-tiger-logo").click();
        page.locator(".test-play-small-button").first().click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(page.querySelector("#test-yes-play")));
        page.querySelector("#test-yes-play").click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNull(page.querySelector("#test-yes-play")));
        assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(4);
        assertThat(page.locator("#sidebar-left .test-passed")).hasCount(1);
        page.querySelector("#test-execution-pane-tab").click();
        assertAll(
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-test_discovered")
            ).hasCount(4),
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-passed")
            ).hasCount(1));
    }

    @Test
    void testRunAScenarioThenCommitInTheExecutionPane() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator(".test-play-button").last().click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(page.querySelector("#test-yes-play")));
        page.querySelector("#test-yes-play").click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNull(page.querySelector("#test-yes-play")));
        assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(3);
        assertThat(page.locator("#sidebar-left .test-passed")).hasCount(2);
        page.querySelector("#test-execution-pane-tab").click();
        assertAll(
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-test_discovered")
            ).hasCount(3),
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-passed")
            ).hasCount(2));
    }

    @Test
    void testShouldRunAllPassingTestsFeatureShouldBePassed() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator(".test-play-button").first().click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(page.querySelector("#test-yes-play")));
        page.querySelector("#test-yes-play").click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNull(page.querySelector("#test-yes-play")));
        page.locator(".test-play-button").first().click();
        await().untilAsserted(() -> assertNotNull(page.querySelector("#test-yes-play")));
        page.querySelector("#test-yes-play").click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNull(page.querySelector("#test-yes-play")));
        assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(1);
        assertThat(page.locator("#sidebar-left .test-passed")).hasCount(4);
        page.querySelector("#test-execution-pane-tab").click();
        assertAll(
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-test_discovered")
            ).hasCount(1),
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-passed")
            ).hasCount(4),
            () -> assertThat(
                page.locator(".test-execution-pane-feature-title").locator(".test-feature-status-word")).hasText(
                "PASSED"));
    }

    @Test
    void testXShouldRunLastFailingTestFeatureShouldBeFailed() {
        page.querySelector("#test-execution-pane-tab").click();
        page.locator(".test-play-button").first().click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNotNull(page.querySelector("#test-yes-play")));
        page.querySelector("#test-yes-play").click();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertNull(page.querySelector("#test-yes-play")));
        assertThat(page.locator("#sidebar-left .test_discovered")).hasCount(0);
        assertThat(page.locator("#sidebar-left .test-passed")).hasCount(4);
        assertThat(page.locator("#sidebar-left .test-failed")).hasCount(1);
        page.querySelector("#test-execution-pane-tab").click();
        assertAll(
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-test_discovered")
            ).hasCount(0),
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-passed")
            ).hasCount(4),
            () -> assertThat(
                page.locator(".test-execution-pane-scenario-title")
                    .locator(".test-status-failed")
            ).hasCount(1),
            () -> assertThat(
                page.locator(".test-play-button")
            ).hasCount(0),
            () -> assertThat(
                page.locator(".test-play-small-button")
            ).hasCount(0),
            () -> assertThat(
                page.locator(".test-execution-pane-feature-title").locator(".test-feature-status-word")).hasText(
                "FAILED"));
    }

}
