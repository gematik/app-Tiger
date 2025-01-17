/*
 *
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

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.concurrent.TimeUnit;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

@TestMethodOrder(MethodOrderer.MethodName.class)
class TrafficVisualisationTest extends AbstractBase {

    @Test
    void test_B_ExecutionPaneActive_TestHaveRunThru() {
        page.querySelector("#test-traffic-visualization-tab").click();
        await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(120, TimeUnit.SECONDS)
                .untilAsserted(
                        () ->
                                PlaywrightAssertions.assertThat(
                                                page.locator("#visualization_pane")).isVisible());
        PlaywrightAssertions.assertThat(page.locator("#test-traffic-visualization-tab.active"))
                .isVisible();
        screenshot(page, "trafficVisualization.png");
    }

    @Test
    void test_C_ExecutionPaneActive_RequestIsClickable() {
        closeSidebar();
        page.querySelector("#test-traffic-visualization-tab").click();
        assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
        assertThat(page.locator(".clickableMessageText").first()).isVisible();
        page.locator(".clickableMessageText").first().click();
        assertThat(page.locator("#rbellog_details_pane")).isVisible();
        assertThat(page.locator("#test-rbel-webui-url")).isVisible();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first())
                .containsText("1");
    }


    @Test
    void test_D_ExecutionPaneActive_ResponseIsClickable() {
        closeSidebar();
        page.querySelector("#test-traffic-visualization-tab").click();
        assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
        assertThat(page.locator(".clickableMessageText").last()).isVisible();
        page.locator(".clickableMessageText").last().click();
        assertThat(page.locator("#rbellog_details_pane")).isVisible();
        assertThat(page.locator("#test-rbel-webui-url")).isVisible();
        assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last())
                .containsText("4");
    }

}
