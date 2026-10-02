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
package de.gematik.test.tiger.playwright.workflowui.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("screenshot")
class XyScreenshotsTest extends AbstractBase {

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(10)
  void screenshotLargeReplayButton() {

    page.querySelector("#test-execution-pane-tab").click();
    page.evaluate(
        "document.getElementsByClassName(\"replay-button\")[0].style.backgroundColor='yellow'");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    page.locator(".test-execution-pane-scenario-title")
        .first()
        .screenshot(
            new Locator.ScreenshotOptions().setPath(getPath("maincontent_replaybutton.png")));
    page.evaluate(
        "document.getElementsByClassName(\"replay-button\")[0].style.removeProperty(\"background-color\")");
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(20)
  void screenshotMainContent() {
    page.querySelector("#test-execution-pane-tab").click();
    screenshotWithHighlightedElementById(
        page, "maincontent_date_highlight.png", "test-execution-pane-date");
    page.evaluate(
        "document.getElementById(\"test-execution-pane-tab\").parentElement.style.backgroundColor='yellow'");
    screenshot(page, "maincontent_tabs_highlight.png");
    page.evaluate(
        "document.getElementById(\"test-execution-pane-tab\").parentElement.style.removeProperty(\"background-color\")");

    screenshotWithHighlightedByClassname(
        page, "featuretitle_highlight.png", "test-execution-pane-feature-title");

    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[0].style.backgroundColor='yellow'");
    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[1].style.backgroundColor='yellow'");
    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[2].style.backgroundColor='yellow'");
    screenshot(page, "maincontent_table_highlight.png");
    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[0].style.removeProperty(\"background-color\")");
    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[1].style.removeProperty(\"background-color\")");
    page.evaluate(
        "document.getElementsByClassName(\"test-feature-status-word\")[2].style.removeProperty(\"background-color\")");
  }

  private void ensureRbelLogClosed() {
    Locator slider = page.locator("#test-webui-slider");

    if (isRbelLogOpen()) {
      slider.click();
      await().atMost(Duration.ofSeconds(2)).until(this::isRbelLogClosed);
    }
  }

  private void ensureRbelLogOpen() {
    Locator slider = page.locator("#test-webui-slider");

    if (isRbelLogClosed()) {
      slider.click();
      await().atMost(Duration.ofSeconds(3)).until(this::isRbelLogOpen);
    }
  }

  private boolean isRbelLogOpen() {
    return page.locator("#test-rbel-logo").isVisible();
  }

  private boolean isRbelLogClosed() {
    return !isRbelLogOpen();
  }

  private String readFirstRbelMessageText() {
    try {
      var frameLocator = page.frameLocator("#rbellog-details-iframe");
      Locator firstMsg = frameLocator.locator(".test-message-number").first();
      if (firstMsg.isVisible()) {
        return firstMsg.innerText();
      }
    } catch (Exception ignored) {
      // ignore and return empty
    }
    return "";
  }

  private void waitForRbelMessageChange(String previous) {
    try {
      await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () -> {
                String current = readFirstRbelMessageText();
                return !current.isEmpty() && !current.equals(previous);
              });
    } catch (Exception ignored) {
      // timeout acceptable; proceed anyway
    }
  }

  @Test
  @Order(30)
  void screenshotMismatchNotesForFindLastRequestWithParameters() {
    // Navigate to the execution pane (where scenario results are shown)
    page.querySelector("#test-execution-pane-tab").click();

    ensureRbelLogClosed();

    // Find and click the scenario in the execution pane table by its name
    Locator mismatchDropdown = page.locator(".mismatch-dropdown");

    Locator failureMessage =
        mismatchDropdown
            .first()
            .locator("..")
            .locator("..")
            .locator("..")
            .locator("..")
            .locator("..");

    failureMessage.scrollIntoViewIfNeeded();
    await().atMost(Duration.ofSeconds(5)).until(failureMessage::isVisible);

    // Take a screenshot of the failure message area (including mismatch notes)
    ensureRbelLogClosed();
    screenshotHighlightedElement(failureMessage, mismatchDropdown, "mismatch_notes_dropbox.png");

    // Step 1: Press the down button to navigate to the next message
    Locator downButton =
        failureMessage.locator(".mismatch-nav-button").nth(1); // second button is down
    // capture current first rbel message text (if available) BEFORE navigation
    String prevMsg = readFirstRbelMessageText();
    downButton.click();
    // keep log open for this screenshot to show updated message
    ensureRbelLogOpen();
    waitForRbelMessageChange(prevMsg);
    screenshotHighlightedElement(failureMessage, downButton, "mismatch_notes_down_button.png");
    // after screenshot we can close again for consistency
    ensureRbelLogClosed();

    // Step 2: Press the up button to navigate to the previous message
    Locator upButton = failureMessage.locator(".mismatch-nav-button").first();
    upButton.click();
    ensureRbelLogClosed();
    screenshotHighlightedElement(failureMessage, upButton, "mismatch_notes_up_button.png");

    // Step 3: Press the selection box and check for 3 options
    Locator dropdown = failureMessage.locator(".mismatch-dropdown");
    dropdown.click();
    ensureRbelLogClosed();
    screenshotPageWithHighlightedElement(dropdown, "mismatch_notes_dropdown.png");

    // Open again (if it closed due to outside click) before keyboard navigation
    dropdown.click();
    page.waitForTimeout(400); // Let panel render

    // Use keyboard navigation to select the third option
    dropdown.press("ArrowDown"); // first
    page.waitForTimeout(80);
    page.keyboard().press("ArrowDown"); // second
    page.waitForTimeout(80);
    page.keyboard().press("ArrowDown"); // third
    page.waitForTimeout(120);

    // Capture focused option BEFORE closing pane (we'll keep reference even if focus changes)
    Locator focusedOption = null;
    if (page.locator(":focus").count() > 0) {
      String ariaActiveDescendant = page.locator(":focus").getAttribute("aria-activedescendant");
      if (ariaActiveDescendant != null && !ariaActiveDescendant.isEmpty()) {
        focusedOption = page.locator("#" + ariaActiveDescendant);
      }
    }

    // Close pane now to ensure clean screenshot (may shift focus, locator still valid)
    ensureRbelLogClosed();

    if (focusedOption != null) {
      failureMessage.scrollIntoViewIfNeeded();
      page.evaluate("window.scrollBy(0, 200)");
      page.waitForTimeout(150);
      screenshotPageWithHighlightedElement(focusedOption, "mismatch_notes_third_option.png");
    }
  }

  /**
   * The scoping toggle sits in the sidebar toolbar alongside quit, pause, and settings buttons, not
   * inside the embedded log's iframe - it applies to every scopable view (Rbel log, traffic
   * visualization) at once.
   */
  private Locator scenarioScopeToggle() {
    return page.locator("#test-sidebar-scenario-scope-toggle");
  }

  private void ensureScenarioScopingReset() {
    // the scope now survives scenario switches, so it has to be switched off explicitly
    Locator toggle = scenarioScopeToggle();
    if (toggle.count() > 0 && "true".equals(toggle.getAttribute("aria-pressed"))) {
      toggle.click();
    }
    Locator selected = page.locator(".test-execution-pane-scenario-title.scenario-selected");
    if (selected.count() > 0) {
      selected.first().click();
    }
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(40)
  void screenshotScenarioScoping() {
    page.querySelector("#test-execution-pane-tab").click();
    ensureRbelLogOpen();
    // don't rely on cross-class ordering to leave scoping/selection clean - a leftover selection
    // from an earlier failed test would otherwise flip our own click below into a deselect.
    ensureScenarioScopingReset();
    page.waitForFunction("() => document.fonts.status === 'loaded'");
    // Wait until the run has actually produced traffic before any screenshot. Run standalone
    // (-Dit.test=...#screenshotScenarioScoping) this test starts the moment the browser connects,
    // when the UI still shows "Waiting for first Feature" and an empty "Loading..." log - which
    // makes for a useless documentation screenshot.
    var frameLocator = page.frameLocator("#rbellog-details-iframe");
    await()
        .atMost(Duration.ofSeconds(120))
        .until(() -> frameLocator.locator(".test-message-number").count() > 0);

    // default (off) state - the toggle lives in the sidebar toolbar with quit, pause, settings
    doActionWithHighlightedElement(
        scenarioScopeToggle(), () -> screenshot(page, "rbellog_scenario_only_toggle.png"));

    // select the first scenario ("Simple Get Request") - its own highlight styling is enough,
    // no extra yellow overlay needed
    Locator firstScenarioTitle = page.locator(".test-execution-pane-scenario-title").first();
    firstScenarioTitle.click();
    // fail loudly here rather than silently screenshotting whatever scenario the toggle falls
    // back to if the click above didn't actually select "Simple Get Request"
    PlaywrightAssertions.assertThat(firstScenarioTitle)
        .hasClass(Pattern.compile(".*scenario-selected.*"));
    firstScenarioTitle.screenshot(
        new Locator.ScreenshotOptions().setPath(getPath("maincontent_scenario_selected.png")));

    scenarioScopeToggle().click();
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        frameLocator
                            .locator(".test-message-number")
                            .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^1$"))))
                    .isVisible());
    doActionWithHighlightedElement(
        scenarioScopeToggle(), () -> screenshot(page, "rbellog_scenario_only_toggle_active.png"));
    page.locator("#rbellog_details_pane")
        .screenshot(
            new Locator.ScreenshotOptions().setPath(getPath("rbellog_scenario_scoped.png")));

    // deselect, then pick a scenario without traffic to show the empty state
    firstScenarioTitle.click();

    Locator noTrafficScenarioTitle =
        page.locator(".test-execution-pane-scenario-title")
            .filter(new Locator.FilterOptions().setHasText("Test zeige HTML"));
    // "Test zeige HTML" lives in the second feature file, which the run only reaches well after
    // the first one finished - wait for it to be listed instead of assuming it already is.
    await()
        .atMost(Duration.ofMinutes(5))
        .pollInterval(Duration.ofSeconds(2))
        .until(() -> noTrafficScenarioTitle.count() > 0);
    noTrafficScenarioTitle.scrollIntoViewIfNeeded();
    noTrafficScenarioTitle.click();
    PlaywrightAssertions.assertThat(noTrafficScenarioTitle)
        .hasClass(Pattern.compile(".*scenario-selected.*"));
    // the scope survives switching scenarios, so it is still on and now points at a scenario
    // that recorded nothing
    PlaywrightAssertions.assertThat(scenarioScopeToggle()).hasAttribute("aria-pressed", "true");
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                PlaywrightAssertions.assertThat(
                        frameLocator.locator("#test-rbel-scenario-only-empty"))
                    .isVisible());
    screenshotOnlyElementById(page, "rbellog_scenario_only_empty.png", "rbellog_details_pane");

    // clean up
    scenarioScopeToggle().click();
    noTrafficScenarioTitle.click();
    ensureRbelLogClosed();
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(45)
  void screenshotTrafficVisualizationScoping() {
    page.querySelector("#test-execution-pane-tab").click();
    ensureScenarioScopingReset();
    page.querySelector("#test-traffic-visualization-tab").click();

    Locator messages = page.locator("#visualization_pane .clickableMessageText");
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(messages.count()).isPositive());
    // captured before scoping is touched - the count scoping must actually shrink below
    int unscopedCount = messages.count();
    screenshot(page, "trafficvisualization_unscoped.png");

    page.querySelector("#test-execution-pane-tab").click();
    Locator firstScenarioTitle = page.locator(".test-execution-pane-scenario-title").first();
    firstScenarioTitle.click();
    PlaywrightAssertions.assertThat(firstScenarioTitle)
        .hasClass(Pattern.compile(".*scenario-selected.*"));
    scenarioScopeToggle().click();
    PlaywrightAssertions.assertThat(scenarioScopeToggle()).hasAttribute("aria-pressed", "true");

    page.querySelector("#test-traffic-visualization-tab").click();
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(messages.count()).isPositive().isLessThan(unscopedCount));
    screenshot(page, "trafficvisualization_scoped.png");

    // clean up
    scenarioScopeToggle().click();
    page.querySelector("#test-execution-pane-tab").click();
    firstScenarioTitle.click();
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(50)
  void screenshotServerLog() {
    page.querySelector("#test-server-log-tab").click();
    screenshot(page, "maincontent_serverlog.png");
    screenshotWithHighlightedElementById(
        page, "maincontent_serverlog_buttons_highlight.png", "test-server-log-pane-buttons");

    page.querySelector("#test-server-log-pane-select").click();
    screenshotWithHighlightedElementById(
        page, "serverlog_level_highlight.png", "test-server-log-pane-select");
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(60)
  void screenshotSmallPlayButton() {

    openSidebar();
    page.evaluate(
        "document.getElementsByClassName(\"small-play-button\")[0].style.backgroundColor='yellow'");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    page.locator("#test-sidebar-featurelistbox")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_replaybutton.png")));
    page.evaluate(
        "document.getElementsByClassName(\"small-play-button\")[0].style.removeProperty(\"background-color\")");
    closeSidebar();
  }

  @Test
  @Order(70)
  void screenshotSubstepToggles() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").first().focus();
    screenshotWithHighlightedByClassname(
        page, "webui_substep_collapsed.png", "test-step-toggle-button");
    page.locator(".test-step-toggle-button").first().click();
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").first().focus();
    screenshotWithHighlightedByClassname(
        page, "webui_substep_partially_expanded.png", "test-step-toggle-button");
    page.locator(".test-step-toggle-button").nth(1).click();
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").nth(1).focus();
    screenshotWithHighlightedByClassname(
        page, "webui_substep_fully_expanded.png", "test-step-toggle-button");
  }

  @Test
  @Order(80)
  void screenshotTopology() {
    page.querySelector("#test-topology-tab").click();

    screenshotOnlyElementByClassname(
        page, "topology_visualizer_button.png", "execution-pane-nav", "#test-topology-tab");

    var diagram = page.locator(".vue-flow");
    PlaywrightAssertions.assertThat(diagram).isVisible();

    page.locator(".vue-flow__controls-fitview").click();

    screenshotOnlyElementByClassname(page, "topology_visualizer_example_diagram.png", "vue-flow");
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(90)
  void screenshotWebUI() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator(".test-message-number").first()));
    externalPage.locator(".test-message-number").first().evaluate("el => el.click()");
    screenshot(externalPage, "webui.png");
    screenshotWithHighlightedByClassname(
        externalPage, "webui_inspect_highlight.png", "test-btn-inspect");
    screenshotWithHighlightedByClassname(
        externalPage, "webui_message_partner.png", "partner-message-button");
    screenshotFullMessageButtonAndPage(externalPage);
    screenshotInspectButton(externalPage);
    screenshotFilterButton(externalPage);
    screenshotExportButton(externalPage);
    screenshotRouteButton(externalPage);
    screenshotRawContentButton(externalPage);
    externalPage.close();
  }

  private void screenshotFullMessageButtonAndPage(Page externalPage) {
    externalPage.evaluate("scrollToMessage('', 10)");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);

    Locator fullMessageButton = externalPage.locator(".full-message-button").first();
    screenshotWithHighlightedByClassname(
        externalPage, "webui_full_message.png", "full-message-button");

    Page singleMessagePage =
        externalPage.waitForPopup(() -> fullMessageButton.evaluate("el => el.click()"));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertNotNull(singleMessagePage.locator(".test-message-number").first()));

    screenshot(singleMessagePage, "webui_single_message_page.png");

    singleMessagePage.close();
  }

  private void screenshotInspectButton(Page externalPage) {
    externalPage.locator(".test-btn-settings").click();
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    externalPage
        .locator("div.sticky-header div.dropdown")
        .locator("div")
        .nth(0)
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("webui_dropup.png")));
    // replaces screenshot(externalPage, "webui_hide_header.png");
    externalPage.locator(".test-btn-settings").click();

    // Use JS click — the inspect button is inside the virtual scroller whose CSS transforms
    // may position it outside the browser viewport
    externalPage.locator(".test-btn-inspect").first().evaluate("el => el.click()");
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#jexlQueryModal #rbelTreeExpressionTextArea"))
        .isEditable();
    screenshot(externalPage, "webui_inspect_open.png");

    var jsElemLookupStr = "document.getElementById(\"btnradioRbelTree\").nextElementSibling.style";
    externalPage.evaluate(jsElemLookupStr + ".backgroundColor='yellow'");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    externalPage
        .locator("#jexlQueryModal")
        .screenshot(
            new Locator.ScreenshotOptions()
                .setPath(getPath("webui_inspect_rbelpath_highlight.png")));
    externalPage.evaluate(jsElemLookupStr + ".removeProperty(\"background-color\")");
    await()
        .atMost(2, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () ->
                assertThat(
                        externalPage.locator("#jexlQueryModal label").first().getAttribute("style"))
                    .isEmpty());
    jsElemLookupStr =
        "document.getElementById(\"btnradioJexlExpression\").nextElementSibling.style";
    externalPage.evaluate(jsElemLookupStr + ".backgroundColor='yellow'");
    externalPage
        .locator("#jexlQueryModal .modal-header .btn-group")
        .locator("label")
        .all()
        .get(1)
        .click();
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    externalPage
        .locator("#jexlQueryModal")
        .screenshot(
            new Locator.ScreenshotOptions().setPath(getPath("webui_inspect_jexl_highlight.png")));
    externalPage.evaluate(jsElemLookupStr + ".removeProperty(\"background-color\")");

    externalPage.locator("#jexlQueryModal .btn-close").click();
  }

  private void screenshotFilterButton(Page externalPage) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator("#filterModalBtn")));

    externalPage.locator("#test-rbel-path-input").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#rbelFilterExpressionTextArea"))
        .isVisible();
    screenshot(externalPage, "webui_filter_open.png");
    externalPage.locator("#filterBackdrop .btn-close").click();
  }

  private void screenshotExportButton(Page externalPage) {
    externalPage.locator(".test-btn-settings").click();
    externalPage.locator("#exportModalButton").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#switchExportWithFilter"))
        .isVisible();
    screenshot(externalPage, "webui_save_open.png");
    externalPage.locator("#saveModalButtonClose").click();
  }

  private void screenshotRouteButton(Page externalPage) {
    externalPage.locator(".test-btn-settings").click();
    externalPage.locator("#routeModalButton").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#routeModal .btn-primary"))
        .isVisible();
    screenshot(externalPage, "webui_routing_open.png");
    externalPage.locator("#routeModal .btn-close").click();
  }

  private void screenshotRawContentButton(Page externalPage) {
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.backgroundColor='yellow'");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[1].style.backgroundColor='yellow'");
    screenshot(externalPage, "webui_btn_content_highlight.png");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.removeProperty(\"background-color\")");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[1].style.removeProperty(\"background-color\")");

    externalPage.locator(".test-modal-content").nth(2).evaluate("el => el.click()");
    externalPage.evaluate(
        "document.getElementById(\"rawContentModal\").children.item(0).children.item(0).style.backgroundColor='yellow'");
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#rawContentModal"))
        .isVisible();
    screenshot(externalPage, "webui_btn_content.png");
    externalPage.evaluate(
        "document.getElementById(\"rawContentModal\").children.item(0).children.item(0).style.removeProperty(\"background-color\")");
  }

  @SuppressWarnings("squid:S2699")
  @Test
  @Order(100)
  void testScreenshotSidebar() {

    screenshot(page, "workflowui.png");
    openSidebar();
    screenshot(page, "sidebaropen.png");
    closeSidebar();
    page.evaluate("document.getElementById(\"sidebar-left\").style.backgroundColor='yellow'");
    page.evaluate(
        "document.getElementById(\"test-tiger-logo\").parentElement.style.backgroundColor='yellow'");
    screenshot(page, "sidebarclosed_highlight.png");
    openSidebar();
    screenshot(page, "sidebaropen_highlight.png");
    page.evaluate(
        "document.getElementById(\"sidebar-left\").style.removeProperty(\"background-color\")");
    page.evaluate(
        "document.getElementById(\"test-tiger-logo\").parentElement.style.removeProperty(\"background-color\")");
    screenshot(page, "sidebaropen.png");
    page.evaluate(
        "document.getElementById(\"sidebar-left\").style.removeProperty(\"background-color\")");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    page.locator("#test-sidebar-quit-icon")
        .locator("..")
        .locator("..")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebarbuttons.png")));

    page.locator("#test-sidebar-statusbox")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_statusbox.png")));
    page.evaluate(
        "document.getElementById(\"test-sidebar-statusbox\").style.backgroundColor='yellow'");
    screenshot(page, "sidebar_statusbox_highlight.png");
    page.evaluate(
        "document.getElementById(\"test-sidebar-statusbox\").style.removeProperty(\"background-color\")");
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    page.locator("#test-sidebar-featurelistbox")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_featurebox.png")));
    page.locator("#test-sidebar-server-status-box")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_serverbox.png")));
    page.locator("#test-sidebar-featurelistbox")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_featurebox.png")));
    page.locator("#test-sidebar-version")
        .locator("..")
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("sidebar_version_build.png")));
  }
}
