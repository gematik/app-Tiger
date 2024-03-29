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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests all feature files and scenarios by name. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class XyFeaturesTest extends AbstractTests {

  @BeforeEach
  void printInfoStarted(TestInfo testInfo) {
    System.out.println("started = " + testInfo.getDisplayName());
  }

  @AfterEach
  void printInfoFinished(TestInfo testInfo) {
    System.out.println("finished = " + testInfo.getDisplayName());
  }

  @Test
  void testFeaturesAndScenarioAmount() {
    page.querySelector("#test-tiger-logo").click();
    assertAll(
        () -> assertThat(page.locator("#test-sidebar-featurelistbox").isVisible()).isTrue(),
        () ->
            assertThat(
                    page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name").count())
                .isEqualTo(NUMBER_OF_FEATURES),
        () ->
            assertThat(
                    page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name")
                        .count())
                .isEqualTo(NUMBER_OF_SCENARIOS),
        () ->
            assertThat(
                    page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-index")
                        .count())
                .isEqualTo(11));
  }

  @Test
  void testFeaturesNames() {
    page.querySelector("#test-tiger-logo").click();
    String featureName =
        page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name")
            .first()
            .textContent();
    String featureName2 =
        page.locator("#test-sidebar-featurelistbox .test-sidebar-feature-name")
            .last()
            .textContent();
    if (featureName.equals("Playwright Test feature")) {
      assertThat(featureName2).isEqualTo("Zweite Feature-Datei");
      assertThat(featureName).isEqualTo("Playwright Test feature");
    } else {
      assertThat(featureName).isEqualTo("Zweite Feature-Datei");
      assertThat(featureName2).isEqualTo("Playwright Test feature");
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
        DELETE Request without body             | 4  | 0 |
        Request with custom header              | 5  | 0 |
        Request with default header             | 6  | 0 |
        Request with custom and default header  | 7  | 0 |
        Request with DataTables Test            | 8  | 0 |
        Request with custom and default header  | 9  | 0 |
        Test <red> with <Dagmar>                | 10 | 1 |
        Test <blue> with <Nils>                 | 11 | 2 |
        Test <green> with <Tim>                 | 12 | 3 |
        Test <yellow> with <Sophie>             | 13 | 4 |
        Test <green> with <foo> again           | 14 | 1 |
        Test <red> with <bar> again             | 15 | 2 |
        Test Find Last Request                  | 16 | 0 |
        Test find last request with parameters  | 17 | 0 |
        Test find last request                  | 18 | 0 |
        JEXL Rbel Namespace Test                | 19 | 1 |
        JEXL Rbel Namespace Test                | 20 | 2 |
        JEXL Rbel Namespace Test                | 21 | 3 |
        JEXL Rbel Namespace Test                | 22 | 4 |
        JEXL Rbel Namespace Test                | 23 | 5 |
        Request a non existing url              | 24 | 0 |
        Test zeige HTML                         | 25 | 0 |
        """)
  void testScenarioNames(String scenarioName, int counter, int index) {
    page.querySelector("#test-tiger-logo").click();
    assertThat(
            page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name")
                .nth(counter)
                .textContent()
                .trim())
        .contains(scenarioName);
    if (index > 0) {
      assertThat(
              page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name")
                  .nth(counter)
                  .locator(".test-sidebar-scenario-index")
                  .textContent())
          .isEqualTo("[" + index + "]");
    }
  }

  @ParameterizedTest()
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
        ${configured_param_name}    | 0  |
        state                       | 1  |
        redirect_uri                | 2  |
        """)
  void testXDataTablesExsistFirstRow(String testName, int counter) {
    page.querySelector("#test-execution-pane-tab").click();
    assertThat(
            page.locator(".table-data-table")
                .nth(4)
                .locator("tr")
                .first()
                .locator("td")
                .nth(counter)
                .textContent())
        .isEqualTo(testName);
  }

  @ParameterizedTest()
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
        client_id                   | 0  |
        ${configured_state_value}   | 1  |
        https://my.redirect         | 2  |
        """)
  void testXDataTablesExsistSecondRow(String testName, int counter) {
    page.querySelector("#test-execution-pane-tab").click();
    assertThat(
            page.locator(".table-data-table")
                .nth(4)
                .locator("tr")
                .last()
                .locator("td")
                .nth(counter)
                .textContent())
        .isEqualTo(testName);
  }

  @Test
  void testScrollingToKlickedLastTestfile() {
    page.querySelector("#test-tiger-logo").click();
    page.locator(".test-sidebar-scenario-name").last().locator(".scenarioLink").click();
    await()
        .untilAsserted(
            () ->
                assertThat(
                        ((Double)
                            page.evaluate(
                                "document.getElementsByClassName('test-execution-pane-scenario-title')[24].getBoundingClientRect().bottom")))
                    .isLessThan(800));

    Double bottom =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[24].getBoundingClientRect().bottom");
    Double top =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[24].getBoundingClientRect().top");
    Integer clientHeight = (Integer) page.evaluate("document.documentElement.clientHeight");
    Integer innerHeight = (Integer) page.evaluate("window.innerHeight");
    var viewHeight = Math.max(clientHeight, innerHeight);
    assertTrue((!(bottom < 0 || top - viewHeight >= 0)), "Scenario is not visible in viewport");
    page.locator(".test-sidebar-scenario-name").first().locator(".scenarioLink").click();
  }

  @Test
  void testScrollingToKlickedFirstTestfile() {
    page.querySelector("#test-tiger-logo").click();
    page.locator(".test-sidebar-scenario-name").first().locator(".scenarioLink").click();
    Double bottom =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[0].getBoundingClientRect().bottom");
    Double top =
        (Double)
            page.evaluate(
                "document.getElementsByClassName('test-execution-pane-scenario-title')[0].getBoundingClientRect().top");
    Integer clientHeight = (Integer) page.evaluate("document.documentElement.clientHeight");
    Integer innerHeight = (Integer) page.evaluate("window.innerHeight");
    var viewHeight = Math.max(clientHeight, innerHeight);
    assertTrue((!(bottom < 0 || top - viewHeight >= 0)), "Scenario is not visible in viewport");
  }

  @Test
  void testRequestsAreCorrectInScenario() {
    page.querySelector("#test-execution-pane-tab").click();
    int count =
        page.locator(".test-execution-pane-scenario-title")
            .first()
            .locator("..")
            .locator(".rbelmessage")
            .count();
    assertThat(count).isEqualTo(2);
    assertThat(
            page.locator(".test-execution-pane-scenario-title")
                .first()
                .locator("..")
                .locator(".rbelmessage")
                .nth(0)
                .locator(".test-rbel-link")
                .textContent())
        .isEqualTo("1");
    assertThat(
            page.locator(".test-execution-pane-scenario-title")
                .first()
                .locator("..")
                .locator(".rbelmessage")
                .nth(1)
                .locator(".test-rbel-link")
                .textContent())
        .isEqualTo("3");
  }
}
