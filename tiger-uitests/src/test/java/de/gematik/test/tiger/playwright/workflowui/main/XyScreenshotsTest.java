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
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
class XyScreenshotsTest extends AbstractBase {

  @SuppressWarnings("squid:S2699")
  @Test
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

  @SuppressWarnings("squid:S2699")
  @Test
  void screenshotMainContent() {
    page.querySelector("#test-execution-pane-tab").click();
    screenshotElementById(page, "maincontent_date_highlight.png", "test-execution-pane-date");
    page.evaluate(
        "document.getElementById(\"test-execution-pane-tab\").parentElement.style.backgroundColor='yellow'");
    screenshot(page, "maincontent_tabs_highlight.png");
    page.evaluate(
        "document.getElementById(\"test-execution-pane-tab\").parentElement.style.removeProperty(\"background-color\")");

    screenshotByClassname(page, "featuretitle_highlight.png", "test-execution-pane-feature-title");

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

  @Test
  void screenshotSubstepToggles() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").first().focus();
    screenshotByClassname(page, "webui_substep_collapsed.png", "test-step-toggle-button");
    page.locator(".test-step-toggle-button").first().click();
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").first().focus();
    screenshotByClassname(page, "webui_substep_partially_expanded.png", "test-step-toggle-button");
    page.locator(".test-step-toggle-button").nth(1).click();
    page.querySelector("#test-execution-pane-tab").click();
    page.locator(".test-step-toggle-button").nth(1).focus();
    screenshotByClassname(page, "webui_substep_fully_expanded.png", "test-step-toggle-button");
  }

  @SuppressWarnings("squid:S2699")
  @Test
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

  @SuppressWarnings("squid:S2699")
  @Test
  void screenshotServerLog() {
    page.querySelector("#test-server-log-tab").click();
    screenshot(page, "maincontent_serverlog.png");
    screenshotElementById(
        page, "maincontent_serverlog_buttons_highlight.png", "test-server-log-pane-buttons");

    page.querySelector("#test-server-log-pane-select").click();
    screenshotElementById(page, "serverlog_level_highlight.png", "test-server-log-pane-select");
  }

  @SuppressWarnings("squid:S2699")
  @Test
  void screenshotWebUI() {
    page.querySelector("#test-execution-pane-tab").click();
    page.locator("#test-webui-slider").click();

    Page externalPage = page.waitForPopup(() -> page.locator("#test-rbel-webui-url").click());
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator(".test-message-number").first()));
    externalPage.locator(".test-message-number").first().click();
    screenshot(externalPage, "webui.png");
    screenshotByClassname(externalPage, "webui_inspect_highlight.png", "test-btn-inspect");

    screenshotByClassname(externalPage, "webui_message_partner.png", "partner-message-button");

    externalPage.locator(".test-btn-settings").click();
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    externalPage
        .locator("div.sticky-header div.dropdown")
        .locator("div")
        .nth(0)
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("webui_dropup.png")));
    // replaces screenshot(externalPage, "webui_hide_header.png");
    externalPage.locator(".test-btn-settings").click();

    externalPage.locator(".test-btn-inspect").first().click();
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

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator("#filterModalBtn")));

    externalPage.locator("#test-rbel-path-input").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#rbelFilterExpressionTextArea"))
        .isVisible();
    screenshot(externalPage, "webui_filter_open.png");
    externalPage.locator("#filterBackdrop .btn-close").click();

    externalPage.locator(".test-btn-settings").click();
    externalPage.locator("#exportModalButton").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#switchExportWithFilter"))
        .isVisible();
    screenshot(externalPage, "webui_save_open.png");
    externalPage.locator("#saveModalButtonClose").click();

    externalPage.locator(".test-btn-settings").click();
    externalPage.locator("#routeModalButton").click();
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#routeModal .btn-primary"))
        .isVisible();
    screenshot(externalPage, "webui_routing_open.png");
    externalPage.locator("#routeModal .btn-close").click();

    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.backgroundColor='yellow'");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[1].style.backgroundColor='yellow'");
    screenshot(externalPage, "webui_btn_content_highlight.png");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.removeProperty(\"background-color\")");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[1].style.removeProperty(\"background-color\")");

    externalPage.locator(".test-modal-content").nth(2).click();
    externalPage.evaluate(
        "document.getElementById(\"rawContentModal\").children.item(0).children.item(0).style.backgroundColor='yellow'");
    com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(
            externalPage.locator("#rawContentModal"))
        .isVisible();
    screenshot(externalPage, "webui_btn_content.png");
    externalPage.evaluate(
        "document.getElementById(\"rawContentModal\").children.item(0).children.item(0).style.removeProperty(\"background-color\")");
    externalPage.close();
  }
}
