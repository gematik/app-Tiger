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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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

  @SuppressWarnings("squid:S2699")
  @Test
  void screenshotLargeReplayButton() {

    page.querySelector("#test-execution-pane-tab").click();
    page.evaluate(
        "document.getElementsByClassName(\"replay-button\")[0].style.backgroundColor='yellow'");
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

    externalPage.locator("#dropdown-hide-button").click();
    externalPage
        .locator("#dropdown-hide-button")
        .locator("div")
        .nth(0)
        .screenshot(new Locator.ScreenshotOptions().setPath(getPath("webui_dropup.png")));
    externalPage.locator("#collapsibleMessageHeaderBtn").click();
    screenshot(externalPage, "webui_hide_header.png");
    externalPage.locator("#dropdown-hide-button").click();
    externalPage.locator("#collapsibleMessageHeaderBtn").click();

    externalPage.locator("#dropdown-hide-button").click();
    externalPage.locator("#collapsibleMessageDetailsBtn").click();
    screenshot(externalPage, "webui_hide_header.png");
    externalPage.locator("#dropdown-hide-button").click();
    externalPage.locator("#collapsibleMessageDetailsBtn").click();

    externalPage.locator(".test-btn-inspect").first().click();
    screenshot(externalPage, "webui_inspect_open.png");
    externalPage.evaluate(
        "document.getElementById(\"rbelTab-name\").style.backgroundColor='yellow'");
    externalPage
        .locator("#jexlQueryModal")
        .screenshot(
            new Locator.ScreenshotOptions()
                .setPath(getPath("webui_inspect_rbelpath_highlight.png")));
    externalPage.evaluate(
        "document.getElementById(\"rbelTab-name\").style.removeProperty(\"background-color\")");
    await()
        .atMost(2, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                assertThat(externalPage.locator("#rbelTab-name").getAttribute("style")).isEmpty());
    externalPage.evaluate(
        "document.getElementById(\"jexlTab-name\").style.backgroundColor='yellow'");
    externalPage.locator("#jexlTab-name").click();
    externalPage
        .locator("#jexlQueryModal")
        .screenshot(
            new Locator.ScreenshotOptions().setPath(getPath("webui_inspect_jexl_highlight.png")));
    externalPage.evaluate(
        "document.getElementById(\"jexlTab-name\").style.removeProperty(\"background-color\")");

    externalPage.locator("#jexlModalButtonClose").click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertNotNull(externalPage.locator("#filterModalBtn")));

    externalPage.locator("#filterModalBtn").click();
    screenshot(externalPage, "webui_filter_open.png");
    externalPage.locator("#filterModalButtonClose").click();

    externalPage.locator("#exportMsgs").click();
    screenshot(externalPage, "webui_save_open.png");
    externalPage.locator("#saveModalButtonClose").click();

    externalPage.locator("#routeModalBtn").click();
    screenshot(externalPage, "webui_routing_open.png");
    externalPage.locator("#routingModalButtonClose").click();

    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.backgroundColor='yellow'");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[1].style.backgroundColor='yellow'");
    screenshot(externalPage, "webui_btn_content_highlight.png");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.removeProperty(\"background-color\")");
    externalPage.evaluate(
        "document.getElementsByClassName(\"test-modal-content\")[0].style.removeProperty(\"background-color\")");
    externalPage.locator(".test-modal-content").nth(2).click();
    screenshot(externalPage, "webui_btn_content.png");
    externalPage.close();
  }
}
