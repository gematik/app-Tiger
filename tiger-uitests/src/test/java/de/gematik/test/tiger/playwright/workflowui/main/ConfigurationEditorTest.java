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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class ConfigurationEditorTest extends AbstractBase {

  protected static final String ENV_MULTILINE_CHECK_KEY = "tgr.testenv.cfg.multiline.check.mode";
  private final String ENV_ICON = ".fa.fa-lg.fa-server";
  private final String PROP_ICON = ".fa.fa-lg.fa-gears";
  private final String FILE_ICON = ".fa.fa-lg.fa-file";

  @BeforeEach
  void setUp() {
    openTgConfigEditor();
  }

  @AfterEach
  void tearDown() {
    closeTgConfigEditor();
  }

  @Test
  void testTigerConfigurationEditorContainsElements() {
    assertThat(page.locator(".vsp__header")).containsText("Tiger Global Configuration Editor");

    assertAll(
        () -> assertThat(page.locator("#test-tg-config-editor-table")).isVisible(),
        () -> assertThat(page.locator("#test-tg-config-editor-btn-close")).isVisible(),
        () -> assertThat(page.locator("button:has-text('Clear filters')")).isVisible());

    Locator headerRow = page.locator("#test-tg-config-editor-table .ag-header-row");
    assertAll(() -> assertThat(headerRow).not().isEmpty(), () -> assertThat(headerRow).isVisible());

    List<String> expectedHeaders = Arrays.asList("Source", "Key", "Value", "Action");
    assertEquals(4, expectedHeaders.size());

    for (String expectedHeader : expectedHeaders) {
      Locator headerCell = headerRow.locator(".ag-header-cell:has-text('" + expectedHeader + "')");
      assertAll(
          () -> assertThat(headerCell).not().isEmpty(), () -> assertThat(headerCell).isVisible());
    }
  }

  @Test
  void testEnvSourceTypeIsVisible() {
    String envKey = "tgr.testenv.cfg.check.mode";
    String envValue = "myEnv";
    String envSource = "ENV";
    testSourceTypeVisibility(envKey, ENV_ICON, envSource, envValue);
  }

  @Test
  void testPropertiesSourceTypeIsVisible() {
    String propSource = "PROPERTIES";
    String propKey = "tgrTestPropCfgCheckMode";
    String propValue = "myProp";
    testSourceTypeVisibility(propKey, PROP_ICON, propSource, propValue);
  }

  @Test
  void testMainYamlSourceTypeIsVisible() {
    String yamlSource = "MAIN_YAML";
    String yamlKey = "tiger.cfgEditor.checkKey";
    String yamlValue = "checkMainValue";
    testSourceTypeVisibility(yamlKey, FILE_ICON, yamlSource, yamlValue);
  }

  @Test
  void testAdditionalYamlSourceTypeIsVisible() {
    String additionalSource = "ADDITIONAL_YAML";
    String additionalKey = "tgrTestAdditionalYaml.cfgEditor.checkKey";
    String additionalValue = "checkAdditionalValue";
    testSourceTypeVisibility(additionalKey, FILE_ICON, additionalSource, additionalValue);
  }

  void testSourceTypeVisibility(String key, String icon, String source, String value) {
    Locator row =
        page.locator(
            "//div[@id='test-tg-config-editor-table']"
                + "//div[@col-id='key' and text()='"
                + key
                + "']/..");

    row.scrollIntoViewIfNeeded();
    assertAll(
        () -> assertThat(row.locator(icon)).isVisible(),
        () -> assertThat(row.locator(icon)).hasAttribute("title", source),
        () -> assertThat(row.locator(".test-tg-config-editor-table-row")).hasText(value));
  }

  @Test
  void testClickExpandButtonWithMultilineEnvSourceType() {
    String envMultilineValue = "Lorem ipsum";
    var row =
        List.of(
            page.locator(
                "//div[@id='test-tg-config-editor-table']"
                    + "//div[@col-id='key' and text()='"
                    + ENV_MULTILINE_CHECK_KEY
                    + "']/.."));

    assertValueTextTruncateAndContains(row, envMultilineValue);
    assertExpandIconExists(row);
    clickExpandIcon(row);

    assertMultilineView(row, envMultilineValue);

    clickCollapseIcon(row);
    assertSimpleView(row, envMultilineValue);
  }

  private void assertValueTextTruncateAndContains(List<Locator> row, String expectedText) {
    assertEquals(
        1,
        row.stream()
            .filter(
                r ->
                    r.locator(".test-tg-config-editor-table-row.text-truncate")
                        .textContent()
                        .contains(expectedText))
            .count());
  }

  private void assertExpandIconExists(List<Locator> row) {
    assertTrue(
        row.stream()
            .allMatch(r -> r.locator(".fa-up-right-and-down-left-from-center").isVisible()));
  }

  private void clickExpandIcon(List<Locator> row) {
    String xpathToExpand =
        "//div[@col-id='key' and text()='"
            + ENV_MULTILINE_CHECK_KEY
            + "']/following-sibling::div[@col-id='value']//i[contains(@class, 'fa-solid') and"
            + " contains(@class, 'fa-up-right-and-down-left-from-center')]";

    row.forEach(r -> r.locator(xpathToExpand).click());
  }

  private void assertMultilineView(List<Locator> row, String expectedText) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertTrue(
                    row.stream()
                        .allMatch(
                            r ->
                                r.locator(".test-tg-config-editor-table-row.text-break.multi-line")
                                    .isVisible())));
    assertFalse(
        row.stream()
            .allMatch(
                r -> r.locator(".test-tg-config-editor-table-row.text-truncate").isVisible()));
    assertTrue(
        row.stream()
            .allMatch(
                r ->
                    r.locator(".test-tg-config-editor-table-row.text-break.multi-line")
                        .textContent()
                        .contains(expectedText)));
  }

  private void clickCollapseIcon(List<Locator> row) {
    String xpathToCollapse =
        "//div[@col-id='key' and text()='"
            + ENV_MULTILINE_CHECK_KEY
            + "']/following-sibling::div[@col-id='value']//i[contains(@class, 'fa-solid') and"
            + " contains(@class, 'fa-down-left-and-up-right-to-center')]";
    row.forEach(r -> r.locator(xpathToCollapse).click());
  }

  private void assertSimpleView(List<Locator> row, String expectedText) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertTrue(
                    row.stream()
                        .allMatch(
                            r ->
                                r.locator(".test-tg-config-editor-table-row.text-truncate")
                                    .isVisible())));
    assertFalse(
        row.stream()
            .allMatch(
                r ->
                    r.locator(".test-tg-config-editor-table-row.text-break.multi-line")
                        .isVisible()));
    assertTrue(
        row.stream()
            .allMatch(
                r ->
                    r.locator(".test-tg-config-editor-table-row.text-truncate")
                        .textContent()
                        .contains(expectedText)));
  }

  @Test
  void testEditEnvValue() {
    String envEditKey = "tgr.testenv.cfg.edit.mode";
    String envEditValue = "editEnv";
    editSourceTypeValue(envEditKey, envEditValue, ENV_ICON);
  }

  @Test
  void testEditPropValue() {
    String propEditKey = "tgrTestPropCfgEditMode";
    String propEditValue = "editProp";
    editSourceTypeValue(propEditKey, propEditValue, PROP_ICON);
  }

  @Test
  void testEditMainYamlValue() {
    String yamlEditKey = "tiger.cfgEditor.editKey";
    String yamlEditValue = "editMainValue";
    editSourceTypeValue(yamlEditKey, yamlEditValue, FILE_ICON);
  }

  @Test
  void testEditAdditionalYamlValue() {
    String additionalEditKey = "tgrTestAdditionalYaml.cfgEditor.editKey";
    String additionalEditValue = "editAdditionalValue";
    editSourceTypeValue(additionalEditKey, additionalEditValue, FILE_ICON);
  }

  void editSourceTypeValue(String key, String value, String iconSelector) {
    String newValue = "Success";
    var row = findRowBySourceTypeKey(key);
    assertRowContainsIcon(row, iconSelector);
    assertThat(row.locator(".test-tg-config-editor-table-row")).hasText(value);
    editRowValueByDoubleClick(value, newValue);

    var updatedRow = findRowBySourceTypeKey(key);
    String RUNTIME_EXPORT_ICON = ".fa.fa-lg.fa-cloud";
    assertRowContainsIcon(updatedRow, RUNTIME_EXPORT_ICON);
    assertThat(updatedRow.locator(".test-tg-config-editor-table-row")).hasText(value + newValue);
  }

  private void openTgConfigEditor() {
    page.querySelector("#test-sidebar-tg-config-editor-icon").click();
  }

  private Locator findRowBySourceTypeKey(String key) {
    return page.locator(
        "//div[@id='test-tg-config-editor-table']"
            + "//div[@col-id='key' and text()='"
            + key
            + "']/..");
  }

  private void assertRowContainsIcon(Locator row, String iconSelector) {
    var icon = row.locator(iconSelector);
    icon.scrollIntoViewIfNeeded();
    assertThat(row.locator(iconSelector)).isVisible();
  }

  private void editRowValueByDoubleClick(String oldValue, String newValue) {
    String xpathToValue =
        "//code[contains(@class, 'test-tg-config-editor-table-row') and text()='" + oldValue + "']";
    page.locator(xpathToValue).dblclick();
    page.waitForSelector("#test-tg-config-editor-text-area");
    page.locator("#test-tg-config-editor-text-area").fill(oldValue + newValue);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> page.locator("#test-tg-config-editor-btn-save").isVisible());
    page.locator("#test-tg-config-editor-btn-save").click();
  }

  private void closeTgConfigEditor() {
    page.locator("#test-tg-config-editor-btn-close").click();
  }

  @Test
  void testDeleteEnv() {
    String envDeleteKey = "tgr.testenv.cfg.delete.mode";
    String envDeleteValue = "deleteEnv";
    testDeleteSourceTypeRow(envDeleteKey, ENV_ICON, envDeleteValue);
  }

  @Test
  void testDeleteProp() {
    String propDeleteKey = "tgrTestPropCfgDeleteMode";
    String propDeleteValue = "deleteProp";
    testDeleteSourceTypeRow(propDeleteKey, PROP_ICON, propDeleteValue);
  }

  @Test
  void testDeleteMainYaml() {
    String yamlDeleteKey = "tiger.cfgEditor.deleteKey";
    String yamlDeleteValue = "deleteMainValue";
    testDeleteSourceTypeRow(yamlDeleteKey, FILE_ICON, yamlDeleteValue);
  }

  @Test
  void testDeleteAdditionalYaml() {
    String additionalDeleteKey = "tgrTestAdditionalYaml.cfgEditor.deleteKey";
    String additionalDeleteValue = "deleteAdditionalValue";
    testDeleteSourceTypeRow(additionalDeleteKey, FILE_ICON, additionalDeleteValue);
  }

  void testDeleteSourceTypeRow(String key, String icon, String value) {
    var row = findRowBySourceTypeKey(key);
    assertRowContainsIcon(row, icon);
    assertThat(row.locator(".test-tg-config-editor-table-row")).hasText(value);
    page.locator(
            "//div[@col-id='key' and text()='"
                + key
                + "']/following-sibling::div[@col-id='action']//button[@data-action='delete']/i[@id='test-tg-config-editor-btn-delete']")
        .click();

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> findRowBySourceTypeKey(key));
  }

  @Test
  void testOpenAndClearFilter() {
    page.locator(".ag-header-icon.ag-header-cell-menu-button").nth(1).click();

    var inputField = page.locator("input[placeholder='Filter...']").first();
    inputField.fill("tgr");
    assertThat(page.locator("span.ag-header-label-icon.ag-filter-icon:not(.ag-hidden)"))
        .isVisible();

    page.locator("#test-tg-config-editor-btn-clear-filters").click();
    assertThat(page.locator("span.ag-header-label-icon.ag-filter-icon:not(.ag-hidden)"))
        .hasCount(0);
    page.locator(".vsp__header h1").click();
  }
}
