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

import static de.gematik.test.tiger.playwright.workflowui.main.ConfigurationEditorTest.ENV_MULTILINE_CHECK_KEY;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConfigEditorScreenshotsTest extends AbstractBase {

  @Test
  void testScreenshotsConfigEditor() {
    PlaywrightAssertions.assertThat(page.locator("#test-sidebar-tg-config-editor-icon"))
        .isVisible();
    page.locator("#test-sidebar-tg-config-editor-icon").hover();
    screenshotWithHighlightedElementById(
        page, "sidebar_config_editor.png", "test-sidebar-tg-config-editor-icon");

    page.querySelector("#test-sidebar-tg-config-editor-icon").click();
    openMenuAndFilter();
    screenshotWithHighlightedByClassname(
        page,
        "config_editor_example_filter_popup.png",
        "ag-labeled ag-label-align-left ag-text-field ag-input-field ag-filter-from"
            + " ag-filter-filter");
    page.locator(".vsp__header").click();
    screenshotWithHighlightedByClassname(page, "tg_global_config_editor.png", "vsp__header");

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> page.locator(".test-tg-config-editor-btn-delete").nth(1).hover());
    screenshotWithHighlightedByClassname(
        page, "config_editor_delete_button.png", "test-tg-config-editor-btn-delete");
    String xpathToValue =
        "//code[contains(@class, 'test-tg-config-editor-table-row') and text()='myEnv']";
    page.locator(xpathToValue).dblclick();
    page.waitForSelector("#test-tg-config-editor-text-area");
    screenshotWithHighlightedElementById(
        page, "config_editor_cell_editor_example.png", "test-tg-config-editor-text-area");
    page.locator("#test-tg-config-editor-btn-cancel").click();
    page.locator(".vsp__header").click();

    screenshotWithHighlightedByClassname(
        page, "config_editor_collapse_icon.png", "fa-solid fa-up-right-and-down-left-from-center");
    String xpathToExpand =
        "//div[@col-id='key' and text()='"
            + ENV_MULTILINE_CHECK_KEY
            + "']/following-sibling::div[@col-id='value']//i[contains(@class, 'fa-solid') and"
            + " contains(@class, 'fa-up-right-and-down-left-from-center')]";

    page.locator(xpathToExpand).click();
    PlaywrightAssertions.assertThat(
            page.locator(".test-tg-config-editor-table-row.text-break.multi-line"))
        .isVisible();

    screenshotWithHighlightedByClassname(
        page, "config_editor_expand_icon.png", "value col-11 gy-1 hljs text-break multi-line");

    page.locator("#test-tg-config-editor-btn-clear-filters").click();
    page.locator("#test-tg-config-editor-btn-close").click();
  }

  private void openMenuAndFilter() {
    page.locator(".ag-header-icon.ag-header-cell-filter-button").nth(1).click();
    var inputField = page.locator(".ag-input-field-input.ag-text-field-input").first();
    inputField.fill("tgr");
    // waiting for the filter icon to have the active class applied, meaning the filter is active.
    PlaywrightAssertions.assertThat(
            page.locator("span.ag-header-icon.ag-header-cell-filter-button.ag-filter-active"))
        .isVisible();
  }
}
