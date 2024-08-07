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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class ConfigurationEditorTest extends AbstractTests {

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
    assertThat(page.locator(".vsp__header").textContent())
        .contains("Tiger Global Configuration Editor");

    assertAll(
        () ->
            assertTrue(
                page.locator("#test-tg-config-editor-table").isVisible(), "Table is not visible"),
        () ->
            assertTrue(
                page.locator("#test-tg-config-editor-btn-close").isVisible(),
                "Close button is not visible"),
        () ->
            assertTrue(
                page.locator("button:has-text('Clear filters')").isVisible(),
                "Clear filters button is not visible"));

    var headerRow = page.locator("#test-tg-config-editor-table .ag-header-row");
    assertAll(
        () -> assertNotNull(headerRow, "Header row is not found"),
        () -> assertTrue(headerRow.isVisible(), "Header row is not visible"));

    List<String> expectedHeaders = Arrays.asList("Source", "Key", "Value", "Action");
    assertEquals(4, expectedHeaders.size());

    for (String expectedHeader : expectedHeaders) {
      var headerCell = headerRow.locator(".ag-header-cell:has-text('" + expectedHeader + "')");
      assertAll(
          () -> assertNotNull(headerCell, "Header cell for " + expectedHeader + " not found"),
          () ->
              assertTrue(
                  headerCell.isVisible(), "Header cell for " + expectedHeader + " is not visible"));
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
    var allRows = page.locator(".ag-row").all();
    var row =
        allRows.stream()
            .filter(r -> r.locator("div[col-id='key']").textContent().equals(key))
            .toList();

    assertAll(
        () -> assertTrue(row.stream().allMatch(r -> r.locator(icon).isVisible())),
        () ->
            assertThat(
                    row.stream()
                        .filter(r -> r.locator(icon).getAttribute("title").equals(source))
                        .count())
                .isEqualTo(1),
        () ->
            assertThat(
                    row.stream()
                        .filter(
                            r ->
                                r.locator(".test-tg-config-editor-table-row")
                                    .textContent()
                                    .equals(value))
                        .count())
                .isEqualTo(1));
  }

  @Test
  void testClickExpandButtonWithMultilineEnvSourceType() {
    String envMultilineValue = "Lorem ipsum";
    var allRows = page.locator(".ag-row").all();

    var row =
        allRows.stream()
            .filter(
                r -> r.locator("div[col-id='key']").textContent().equals(ENV_MULTILINE_CHECK_KEY))
            .toList();

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
    assertTrue(
        row.stream()
            .anyMatch(
                r -> r.locator(".test-tg-config-editor-table-row").textContent().equals(value)));

    editRowValueByDoubleClick(value, newValue);

    var updatedRow = findRowBySourceTypeKey(key);
    String RUNTIME_EXPORT_ICON = ".fa.fa-lg.fa-cloud";
    assertRowContainsIcon(updatedRow, RUNTIME_EXPORT_ICON);
    assertTrue(
        updatedRow.stream()
            .anyMatch(
                r ->
                    r.locator(".test-tg-config-editor-table-row")
                        .textContent()
                        .equals(value + newValue)));
  }

  private void openTgConfigEditor() {
    page.querySelector("#test-sidebar-tg-config-editor-icon").click();
  }

  private List<Locator> findRowBySourceTypeKey(String key) {
    var allRows = page.locator(".ag-row").all();
    return allRows.stream()
        .filter(r -> r.locator("div[col-id='key']").textContent().equals(key))
        .toList();
  }

  private void assertRowContainsIcon(List<Locator> row, String iconSelector) {
    assertTrue(row.stream().allMatch(r -> r.locator(iconSelector).isVisible()));
  }

  private void editRowValueByDoubleClick(String oldValue, String newValue) {
    String xpathToValue =
        "//code[contains(@class, 'test-tg-config-editor-table-row') and text()='" + oldValue + "']";
    page.locator(xpathToValue).dblclick();
    page.waitForSelector("#test-tg-config-editor-text-area");
    page.locator("#test-tg-config-editor-text-area").fill(oldValue + newValue);
    await().untilAsserted(() -> page.locator("#test-tg-config-editor-btn-save").isVisible());
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
    assertTrue(
        row.stream()
            .anyMatch(
                r -> r.locator(".test-tg-config-editor-table-row").textContent().equals(value)));

    page.locator(
            "//div[@col-id='key' and text()='"
                + key
                + "']/following-sibling::div[@col-id='action']//button[@data-action='delete']/i[@id='test-tg-config-editor-btn-delete']")
        .click();

    await()
        .untilAsserted(
            () -> {
              List<Locator> matchingRows =
                  row.stream()
                      .filter(
                          r ->
                              r.locator(".test-tg-config-editor-table-row")
                                  .textContent()
                                  .equals(value))
                      .toList();

              assertThat(matchingRows).isEmpty();
            });
  }

  @Test
  void testOpenAndClearFilter() {
    page.locator(".ag-header-icon.ag-header-cell-menu-button").nth(1).click();

    var inputField = page.locator("input[placeholder='Filter...']").first();
    inputField.fill("tgr");
    PlaywrightAssertions.assertThat(
            page.locator("span.ag-header-label-icon.ag-filter-icon:not(.ag-hidden)"))
        .isVisible();

    page.locator("#test-tg-config-editor-btn-clear-filters").click();
    PlaywrightAssertions.assertThat(
            page.locator("span.ag-header-label-icon.ag-filter-icon:not(.ag-hidden)"))
        .hasCount(0);
    page.locator(".vsp__header h1").click();
  }
}
