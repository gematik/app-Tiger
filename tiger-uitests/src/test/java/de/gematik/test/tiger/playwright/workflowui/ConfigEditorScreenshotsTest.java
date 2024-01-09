/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.playwright.workflowui;

import static de.gematik.test.tiger.playwright.workflowui.ConfigurationEditorTest.ENV_MULTILINE_CHECK_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;

class ConfigEditorScreenshotsTest extends AbstractTests {

  @Test
  void testScreenshotsConfigEditor() {
    assertThat(page.locator("#test-sidebar-tg-config-editor-icon").isVisible()).isTrue();
    page.locator("#test-sidebar-tg-config-editor-icon").hover();
    screenshot(page, "sidebar_config_editor.png", "test-sidebar-tg-config-editor-icon", true);

    page.querySelector("#test-sidebar-tg-config-editor-icon").click();
    openMenuAndFilter();
    screenshot(
        page,
        "config_editor_example_filter_popup.png",
        "ag-labeled ag-label-align-left ag-text-field ag-input-field ag-filter-from"
            + " ag-filter-filter",
        false);
    await().untilAsserted(() -> page.locator(".vsp__header").isVisible());
    page.locator(".vsp__header").click();
    screenshot(page, "tg_global_config_editor.png", "vsp__header", false);

    await().untilAsserted(() -> page.locator("#test-tg-config-editor-btn-delete").nth(1).hover());
    screenshot(page, "config_editor_delete_button.png", "test-tg-config-editor-btn-delete", true);

    String xpathToValue = "//code[@id='test-tg-config-editor-table-row' and text()='myEnv']";
    page.locator(xpathToValue).dblclick();
    page.waitForSelector("#test-tg-config-editor-text-area");
    screenshot(
        page, "config_editor_cell_editor_example.png", "test-tg-config-editor-text-area", true);
    page.locator("#test-tg-config-editor-btn-cancel").click();
    page.locator(".vsp__header").click();

    screenshot(
        page,
        "config_editor_collapse_icon.png",
        "fa-solid fa-up-right-and-down-left-from-center",
        false);
    String xpathToExpand =
        "//div[@col-id='key' and text()='"
            + ENV_MULTILINE_CHECK_KEY
            + "']/following-sibling::div[@col-id='value']//i[contains(@class, 'fa-solid') and"
            + " contains(@class, 'fa-up-right-and-down-left-from-center')]";

    page.locator(xpathToExpand).click();
    await()
        .untilAsserted(
            () ->
                page.locator("#test-tg-config-editor-table-row.text-break.multi-line").isVisible());

    screenshot(
        page,
        "config_editor_expand_icon.png",
        "value col-11 gy-1 hljs text-break multi-line",
        false);

    page.locator("#test-tg-config-editor-btn-clear-filters").click();
    page.locator("#test-tg-config-editor-btn-close").click();
  }

  private void openMenuAndFilter() {
    page.locator(".ag-header-icon.ag-header-cell-menu-button .ag-icon.ag-icon-menu")
        .nth(1)
        .dblclick();
    var inputField = page.locator("input[placeholder='Filter...']");
    inputField.type("tgr");
    await().untilAsserted(() -> page.locator("#test-tg-config-editor-table").isVisible());
  }
}
