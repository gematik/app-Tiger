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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import de.gematik.test.tiger.playwright.workflowui.AbstractBase;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests all feature files and scenarios by name. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class XyFeaturesTest extends AbstractBase {

  @Test
  void testFeaturesAndScenarioAmount() {
    openSidebar();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-featurelistbox")).isVisible(),
        () ->
            assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name"))
                .hasCount(NUMBER_OF_FEATURES),
        () ->
            assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name"))
                .hasCount(NUMBER_OF_SCENARIOS),
        () ->
            assertThat(page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-index"))
                .hasCount(11));
  }

  @Test
  void testFeaturesNames() {
    openSidebar();
    Locator featureName =
        page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name").first();
    Locator featureName2 =
        page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name").last();
    if (featureName.innerText().equals("Playwright Test feature")) {
      assertThat(featureName2).containsText("Zweite Feature-Datei");
      assertThat(featureName).containsText("Playwright Test feature");
    } else {
      assertThat(featureName).containsText("Zweite Feature-Datei");
      assertThat(featureName2).containsText("Playwright Test feature");
    }
  }

  @ParameterizedTest()
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                Simple Get Request                      | 0  | 0 |
                Get Request to folder                   | 1  | 0 |
                PUT Request to folder                   | 2  | 0 |
                PUT Request with body to folder         | 3  | 0 |
                DELETE Request without body shall fail  | 4  | 0 |
                Request with custom header              | 5  | 0 |
                Request with default header             | 6  | 0 |
                Request with custom and default header  | 7  | 0 |
                Request with DataTables Test            | 8  | 0 |
                Request with custom and default header  | 9  | 0 |
                Test red with Dagmar                    | 10 | 1 |
                Test blue with Nils                     | 11 | 2 |
                Test green with Tim                     | 12 | 3 |
                Test yellow with Sophie                 | 13 | 4 |
                Test green with foo again               | 14 | 1 |
                Test red with bar again                 | 15 | 2 |
                Test Find Last Request                  | 16 | 0 |
                Test find last request with parameters  | 17 | 0 |
                Test find last request                  | 18 | 0 |
                JEXL Rbel Namespace Test                | 19 | 1 |
                JEXL Rbel Namespace Test                | 20 | 2 |
                JEXL Rbel Namespace Test                | 21 | 3 |
                JEXL Rbel Namespace Test                | 22 | 4 |
                JEXL Rbel Namespace Test                | 23 | 5 |
                Request a non existing url              | 24 | 0 |
                Request for testing tooltips            | 25 | 0 |
                A scenario with substeps                | 26 | 0 |
                Test zeige HTML                         | 27 | 0 |
                """)
  void testScenarioNames(String scenarioName, int counter, int index) {
    openSidebar();
    assertThat(
            page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name").nth(counter))
        .containsText(scenarioName);
    if (index > 0) {
      assertThat(
              page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name")
                  .nth(counter)
                  .locator(".test-sidebar-scenario-index"))
          .containsText("[" + index + "]");
    }
  }

  @ParameterizedTest()
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                my_cool_param               | 0  |
                state                       | 1  |
                redirect_uri                | 2  |
                """)
  void testXDataTablesExistFirstRow(String testName, int counter) {
    page.querySelector("#test-execution-pane-tab").click();
    assertThat(
            page.locator(".table-data-table")
                .nth(4)
                .locator("tr")
                .first()
                .locator("td")
                .nth(counter))
        .containsText(testName);
  }

  @ParameterizedTest()
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
                client_id                   | 0  |
                some_value                  | 1  |
                https://my.redirect         | 2  |
                """)
  void testXDataTablesExistSecondRow(String testName, int counter) {
    page.querySelector("#test-execution-pane-tab").click();
    assertThat(
            page.locator(".table-data-table")
                .nth(4)
                .locator("tr")
                .last()
                .locator("td")
                .nth(counter))
        .containsText(testName);
  }

  @Test
  void testScrollingToClickedLastTestFile() {
    openSidebar();
    page.locator(".test-sidebar-scenario-name").last().locator(".scenarioLink").click();
    int lastArray = NUMBER_OF_SCENARIOS - 1;
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                org.assertj.core.api.Assertions.assertThat(
                        ((Double)
                            page.evaluate(
                                "document.getElementsByClassName('test-execution-pane-scenario-title')[\""
                                    + lastArray
                                    + "\"].getBoundingClientRect().bottom")))
                    .isLessThan(800));

    Double bottom =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[\""
                    + lastArray
                    + "\"].getBoundingClientRect().bottom");
    Double top =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[\""
                    + lastArray
                    + "\"].getBoundingClientRect().top");
    Integer clientHeight = (Integer) page.evaluate("document.documentElement.clientHeight");
    Integer innerHeight = (Integer) page.evaluate("window.innerHeight");
    var viewHeight = Math.max(clientHeight, innerHeight);
    assertTrue((!(bottom < 0 || top - viewHeight >= 0)), "Scenario is not visible in viewport");
    page.locator(".test-sidebar-scenario-name").first().locator(".scenarioLink").click();
  }

  @Test
  void testScrollingToClickedFirstTestfile() {
    openSidebar();
    page.locator(".test-sidebar-scenario-name").first().locator(".scenarioLink").click();
    assertThat(page.locator(".test-execution-pane-scenario-title").first()).isVisible();
  }

  @Test
  void testRequestsAreCorrectInScenario() {
    var numberOfExpectedMessages = 4;
    page.locator("#test-execution-pane-tab").click();

    var rbelMessages =
        page.locator(".test-execution-pane-scenario-title")
            .first()
            .locator("..")
            .locator(".rbelmessage");
    assertThat(rbelMessages).hasCount(numberOfExpectedMessages);
    for (var i = 0; i < numberOfExpectedMessages; i++) {
      assertThat(rbelMessages.nth(i).locator(".test-rbel-link"))
          .containsText(String.valueOf(i + 1));
    }
  }

  @Test
  void testWhetherTitleContainsOriginal() {
    page.querySelector("#test-execution-pane-tab").click();
    Locator last =
        page.locator(".test-execution-pane-scenario-title")
            .first()
            .locator("..")
            .locator(".table-test-steps")
            .locator("tr")
            .last()
            .locator("td")
            .last()
            .locator(".test-step-description");
    String title = last.getAttribute("title");

    assertEquals(
        "And TGR assert \"!{rbel:currentRequestAsString('$.method')}\" matches \"GET\"", title);
  }
}
