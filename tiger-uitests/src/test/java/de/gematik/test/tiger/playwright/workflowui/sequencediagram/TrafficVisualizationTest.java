/*
 * Copyright 2025 gematik GmbH
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
package de.gematik.test.tiger.playwright.workflowui.sequencediagram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
class TrafficVisualizationTest extends AbstractBase {

  @Test
  void test_B_ExecutionPaneActive_TestHaveRunThru() {
    page.locator("#test-traffic-visualization-tab").click();
    // we want to wait for the .clickableMessageText to be sure that we really have a .svg and not
    // only the wrapping div
    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(120, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(page.locator(".clickableMessageText").first()).isVisible());
    assertThat(page.locator("#test-traffic-visualization-tab.active")).isVisible();
    screenshot(page, "trafficVisualization.png");
  }

  @Test
  void test_C_ExecutionPaneActive_RequestIsClickable() {
    closeSidebar();
    page.locator("#test-traffic-visualization-tab").click();
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
    page.locator("#test-traffic-visualization-tab").click();
    assertThat(page.locator("#rbellog_details_pane")).not().isVisible();
    assertThat(page.locator(".clickableMessageText").last()).isVisible();
    page.locator(".clickableMessageText").last().click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    assertThat(page.locator("#test-rbel-webui-url")).isVisible();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").last())
        .containsText("4");
  }
}
