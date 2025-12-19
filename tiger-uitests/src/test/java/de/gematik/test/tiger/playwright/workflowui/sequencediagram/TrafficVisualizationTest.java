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
package de.gematik.test.tiger.playwright.workflowui.sequencediagram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Slf4j
@TestMethodOrder(MethodOrderer.MethodName.class)
class TrafficVisualizationTest extends AbstractBase {

  @Test
  void test_B_ExecutionPaneActive_TestHaveRunThru() {
    showTrafficVisualizationPane();
    screenshot(page, "trafficVisualization.png");
  }

  @Test
  void test_C_ExecutionPaneActive_RequestIsClickable() {
    closeSidebar();
    showTrafficVisualizationPane();
    page.locator(".clickableMessageText").first().click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    assertThat(page.locator("#test-rbel-webui-url")).isVisible();
    assertThat(page.frameLocator("#rbellog-details-iframe").locator(".test-message-number").first())
        .containsText("1");
  }

  @Test
  void test_D_ExecutionPaneActive_ResponseIsClickable() {
    closeSidebar();
    showTrafficVisualizationPane();
    assertThat(page.locator(".clickableMessageText").last()).isVisible();
    page.locator(".clickableMessageText").last().click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();
    assertThat(page.locator("#test-rbel-webui-url")).isVisible();
    // Because the configuration uses a remote proxy, the sequence numbers are not generated all
    // on the local tiger proxy and the sequence is not exactly the same as the time order.
    assertThat(
            page.frameLocator("#rbellog-details-iframe")
                .locator(".test-message-number")
                .getByText("2"))
        .isVisible();
  }

  private void showTrafficVisualizationPane() {
    int ctr = 0;
    while (ctr < 3 && !page.locator("#visualization_pane").isVisible()) {
      page.querySelector("#test-traffic-visualization-tab").click();
      try {
        await()
            .atMost(Duration.ofSeconds(5L))
            .until(page.locator("#visualization_pane")::isVisible);
      } catch (ConditionTimeoutException cte) {
        try {
          Files.createDirectories(Path.of("./target/playwright-artifacts"));
          screenshot(
              page,
              "./target/playwright-artifacts/OpenVisualizationPaneFailed"
                  + ctr
                  + "-"
                  + UUID.randomUUID()
                  + ".png");
        } catch (IOException e) {
          log.error("Unable to save screenshot while failing to open vis traffic pane", e);
        }
        ctr++;
      }
    }
    // we want to wait for the .clickableMessageText to be sure that we really have a .svg and not
    // only the wrapping div
    await()
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .atMost(120, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(page.locator(".clickableMessageText").first()).isVisible());
    assertThat(page.locator("#test-traffic-visualization-tab.active")).isVisible();
  }
}
