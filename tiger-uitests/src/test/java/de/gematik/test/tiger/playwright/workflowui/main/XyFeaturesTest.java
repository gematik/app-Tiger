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
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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
                .hasCount(NUMBER_OF_SCENARIOS_INCLUDING_OUTLINE_NODES),
        () ->
            assertThat(
                    page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-with-index"))
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

  /**
   * Provides test data for scenario names with automatically computed counters. Each pair of
   * scenario name and example index gets assigned a counter based on its position in the list.
   */
  private static Stream<Arguments> provideScenarioData() {
    return Stream.iterate(0, i -> i < SCENARIO_DATA_WITH_OUTLINE_NODES.length, i -> i + 1)
        .map(
            i ->
                Arguments.of(
                    SCENARIO_DATA_WITH_OUTLINE_NODES[i][0], // scenarioName
                    i, // counter (automatically computed from position)
                    Integer.parseInt(SCENARIO_DATA_WITH_OUTLINE_NODES[i][1]) // index
                    ));
  }

  @ParameterizedTest(name = "{0} - counter: {1} index: {2}")
  @MethodSource("provideScenarioData")
  void testScenarioNames(String scenarioName, int counter, int index) {
    openSidebar();
    var scenarioLine =
        page.locator("#test-sidebar-featurelistbox .test-sidebar-scenario-name").nth(counter);
    assertThat(scenarioLine).containsText(scenarioName);
    if (index > 0) {
      var scenarioLink = scenarioLine.locator(".scenarioLink");
      assertThat(scenarioLink).containsClass("test-sidebar-scenario-with-index");
      assertThat(scenarioLink).containsText("[" + index + "]");
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
