/*
 *
 * Copyright 2026 gematik GmbH
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

package de.gematik.test.tiger.playwright.workflowui.scenarioscope;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.FilterOptions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class ScenarioScopeTest extends AbstractBase {

  private static final int LAST_MESSAGE_NUMBER = 22;

  @Test
  void scenarioOnlyToggleShowsTrafficFromTheLastScenario() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();
    assertThat(page.locator("#rbellog_details_pane")).isVisible();

    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    Locator toggle = page.locator("#test-sidebar-scenario-scope-toggle");
    assertThat(toggle).isVisible();
    assertThat(toggle).hasAttribute("aria-pressed", "false");
    assertThat(frameLocator.locator("#test-rbel-scenario-only-empty")).not().isVisible();

    Locator lastMessage =
        frameLocator
            .locator(".test-message-number")
            .filter(
                new FilterOptions()
                    .setHasText(Pattern.compile("^" + LAST_MESSAGE_NUMBER + "$")));
    assertThat(lastMessage).not().isVisible();

    toggle.click();
    assertThat(toggle).hasAttribute("aria-pressed", "true");
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(lastMessage).isVisible());
  }

  @Test
  void testFeatureBoxClickOnLastScenario() {
      openSidebar();
      Locator lastSidebarScenario = page.locator(".test-sidebar-scenario-name").last();
      Locator lastExecutionPaneScenario = page.locator(".test-execution-pane-scenario-title").last();

      String scenarioTitleBeforeClick = lastExecutionPaneScenario.textContent().trim();
      lastSidebarScenario.locator(".scenarioLink").click();

      assertAll(
          () ->
              Assertions.assertThat(lastExecutionPaneScenario.textContent().trim())
                  .startsWith(scenarioTitleBeforeClick),
          () ->
              Assertions.assertThat(lastSidebarScenario.getAttribute("title"))
                  .isEqualTo("Selected for Rbel log scoping - click to deselect"),
          () ->
              Assertions.assertThat(lastExecutionPaneScenario.getAttribute("title"))
                  .isEqualTo("Selected for Rbel log scoping - click to deselect"));
  }
}
