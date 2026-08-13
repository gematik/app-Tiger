/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.playwright.workflowui.report;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import com.microsoft.playwright.Playwright;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class SerenityReportTest {

  // Selectors
  private static final String SCENARIO_TABLE = "#tests #scenario-results";
  private static final String SCENARIO_FIRST_ROW_RESULT_CELL = "#tests #scenario-results tbody tr:first-child td.test-result-cell";
  private static final String RESULT_HEADER_TH = "#tests #scenario-results thead th:has-text('Result')";

  private Playwright playwright;
  private Browser browser;
  private Page page;

  @BeforeAll
  void initBrowserAndPage() {
    Path reportPath = Paths.get("target", "site", "serenity", "index.html");
    assertTrue(
        Files.exists(reportPath),
        "Serenity report not found at " + reportPath + ". Run the base report generation first.");

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    page = browser.newPage();
    page.navigate(reportPath.toAbsolutePath().normalize().toUri().toString());
  }

  @BeforeEach
  void reloadPageBeforeEach() {
    if (page != null) {
      try {
        page.reload();
        // ensure initial DOM is ready
        page.waitForSelector(SCENARIO_TABLE, new WaitForSelectorOptions().setTimeout(1500));
      } catch (Exception e) {
        // ignore reload failures; tests will fail with clearer messages
      }
    }
  }

  @AfterAll
  void closeBrowserAndPlaywright() {
    if (page != null) page.close();
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Test
  void shouldShowSerenityReportPage() {
    assertThat(page.locator("body")).containsText("Serenity");
  }

  @Test
  void shouldHaveValueForEachKeyStatisticsEntry() {
    Locator keyStatisticsHeading =
        page.locator("h1,h2,h3,h4,h5,h6")
            .filter(new Locator.FilterOptions().setHasText("Key Statistics"))
            .first();
    assertTrue(keyStatisticsHeading.isVisible(), "Could not find 'Key Statistics' heading.");

    Locator keyStatisticsTable = keyStatisticsHeading.locator("xpath=following::table[1]");
    assertTrue(keyStatisticsTable.isVisible(), "Could not find table below 'Key Statistics' heading.");
    assertKeyStatisticsValues(keyStatisticsTable);
  }

  @Test
  void shouldHaveValueForEachKeyStatisticsEntryInTestResults() {
    openTestResultsTab();
    Locator keyStatisticsTable = page.locator("#tests h3:has-text('Key Statistics') + div table").first();
    assertTrue(
        keyStatisticsTable.count() > 0, "Could not find Key Statistics table in 'Test Results' tab.");
    assertKeyStatisticsValues(keyStatisticsTable);
  }

  @Test
  void shouldSetEntriesPerPageTo100InTestResults() {
    openTestResultsTab();
    setEntriesPerPageTo100InScenarioResults();
  }

  @Test
  void shouldSortResultColumnAndShowFailureFirst() {
    openTestResultsTab();
    setEntriesPerPageTo100InScenarioResults();

    Locator allFailureIcons = page.locator(SCENARIO_TABLE + " tbody td.test-result-cell i.failure-icon");
    int failures = allFailureIcons.count();
    assertTrue(failures > 0, "No negative test result (red X) found in the Result column. Found failures: " + failures);

    Locator resultHeader = page.locator(RESULT_HEADER_TH).first();
    assertTrue(resultHeader.count() > 0, "Could not find 'Result' column header.");

    boolean failureAtTop = false;
    for (int i = 0; i < 3; i++) {
      resultHeader.click();
      try {
        page.waitForSelector(SCENARIO_FIRST_ROW_RESULT_CELL);
      } catch (Exception e) {
        // ignore timeout
      }
      Locator firstRowFailureIcon =
          page.locator(
              SCENARIO_FIRST_ROW_RESULT_CELL + " i.failure-icon, " + SCENARIO_FIRST_ROW_RESULT_CELL + " i[title='FAILURE']");
      if (firstRowFailureIcon.count() > 0) {
        failureAtTop = true;
        break;
      }
    }

    assertTrue(
        failureAtTop,
        "After sorting by 'Result', the first row does not show a negative result (red X).");
  }

  private void setEntriesPerPageTo100InScenarioResults() {
    page.waitForSelector(SCENARIO_TABLE, new WaitForSelectorOptions().setTimeout(1500));

    Locator entriesPerPageSelect =
        page.locator(
                "#tests select[aria-controls='scenario-results'],"
                    + SCENARIO_TABLE + "_wrapper select,"
                    + "#tests .dt-length select")
            .first();

    if (entriesPerPageSelect.count() > 0) {
      entriesPerPageSelect.selectOption("100", new Locator.SelectOptionOptions().setForce(true));
      assertThat(entriesPerPageSelect).hasValue("100");
      assertScenarioResultsContainEntries();
      return;
    }

    boolean updatedViaDataTableApi =
        Boolean.TRUE.equals(
            page.evaluate(
                "() => {"
                    + "  const jq = window.jQuery;"
                    + "  if (!jq || !jq.fn || !jq.fn.dataTable || jq('#scenario-results').length === 0) {"
                    + "    return false;"
                    + "  }"
                    + "  const table = jq('#scenario-results').DataTable();"
                    + "  table.page.len(100).draw();"
                    + "  return table.page.len() === 100;"
                    + "}"));

    assertTrue(
        updatedViaDataTableApi,
        "Could not find entries-per-page selector and DataTables API fallback failed. DataTables reports page.len()=100? " + updatedViaDataTableApi);

    assertScenarioResultsContainEntries();
  }

  private void openTestResultsTab() {
    Locator testResultsTab = page.locator("a[data-toggle='tab'][href='#tests']");
    assertTrue(testResultsTab.isVisible(), "Could not find 'Test Results' tab.");
    testResultsTab.click();
  }

  private void assertKeyStatisticsValues(Locator keyStatisticsTable) {
    Locator rows = keyStatisticsTable.locator("tbody tr");
    int rowCount = rows.count();
    assertTrue(rowCount > 0, "Key Statistics table is empty.");

    for (int i = 0; i < rowCount; i++) {
      Locator row = rows.nth(i);
      Locator cells = row.locator("td");
      int cellCount = cells.count();
      assertTrue(cellCount >= 2 && cellCount % 2 == 0, "Row " + i + " has invalid key/value columns.");

      for (int j = 0; j < cellCount; j += 2) {
        String key = cells.nth(j).innerText().trim();
        String value = cells.nth(j + 1).innerText().trim();
        assertFalse(key.isEmpty(), "Row " + i + " has an empty key.");
        assertFalse(value.isEmpty(), "Key '" + key + "' has no value.");
      }
    }
  }

  private void assertScenarioResultsContainEntries() {
    Locator rows = page.locator("#tests #scenario-results tbody tr");
    int rowCount = rows.count();
    assertTrue(rowCount > 0, "Scenario results table has no rows.");

    String firstRowText = rows.first().innerText().trim();
    assertFalse(
        firstRowText.equalsIgnoreCase("No data available in table"),
        "Scenario results table has no entries.");
  }
}
