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
package de.gematik.test.tiger.maven.adapter.mojos;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TigerSerenityReportMojoTest {

  @Mock private Log log;

  @TempDir private Path reportDir;

  private TigerSerenityReportMojo underTest;

  @BeforeEach
  void setUp() {
    underTest = new TigerSerenityReportMojo();
    underTest.setLog(log);
    underTest.setReportDirectory(reportDir.toFile());
    underTest.setRequirementsBaseDir("src/test/resources");
    underTest.setReports(List.of("html", "single-page-html", "json-summary"));
  }

  @Test
  @DisplayName("If the report directory does not exist, a warning should be shown")
  @SneakyThrows
  void testIfTheReportDirectoryDoesNotExistAWarningShouldBeShown() {
    // Preparation
    Files.delete(reportDir);

    // Execution
    underTest.execute();

    // Assertion
    verify(log).warn("Report directory does not exist yet: " + reportDir);
  }

  @Test
  @DisplayName("Should contain all relevant report info with failure data")
  @SneakyThrows
  void testShouldContainAllRelevantReportInfoWithFailureData() {
    // Preparation
    prepareReportDir();

    // Execution
    executeIgnoringMojoFailureException();

    // Assertion
    var serenitySummaryHtmlContent = readString(reportDir.resolve("serenity-summary.html"));
    assertAll(
        () ->
            assertThat(
                readAllLines(reportDir.resolve("results.csv")),
                containsInAnyOrder(
                    startsWith("\"Story\",\"Title\",\"Result\","),
                    startsWith("\"Test Tiger BDD\",\"Simple first test\",\"SUCCESS\","),
                    startsWith("\"Test Tiger BDD\",\"Simple second test\",\"FAILURE\","),
                    startsWith("\"Test Tiger BDD\",\"Simple third test\",\"SUCCESS\","))),
        () -> assertThat(serenitySummaryHtmlContent, containsString("2 passing tests")),
        () -> assertThat(serenitySummaryHtmlContent, containsString("1 failing test")),
        () ->
            assertThat(
                serenitySummaryHtmlContent,
                containsString(
                    "Assertion error: Expecting actual:  &quot;jetty-dir.css&quot;to match pattern:"
                        + "  &quot;mööööp&quot;")),
        () -> assertTrue(Files.exists(reportDir.resolve("index.html")), "index.html exists"));
  }

  @Test
  @SneakyThrows
  void testShouldContainUnresolvedFolderWIthJsonFiles() {
    // Preparation
    prepareReportDir();

    // Execution
    executeIgnoringMojoFailureException();

    // Assertion
    File[] jsonFiles = reportDir.resolve("unresolvedReports").toFile().listFiles();
    AssertionsForClassTypes.assertThat(jsonFiles)
        .hasSize(3)
        .allMatch(jsonFile -> jsonFile.getName().endsWith(".json"));
  }

  @ParameterizedTest
  @MethodSource("provideReportTypes")
  @SneakyThrows
  void testShouldOnlyCreateConfiguredReports(
      String reportType, String expectedFile, List<String> shouldNotExistFiles) {
    prepareReportDir();
    underTest.setReports(List.of(reportType));
    executeIgnoringMojoFailureException();
    val filesInReportDir = reportDir.toFile().list();
    Assertions.assertThat(filesInReportDir)
        .contains(expectedFile)
        .doesNotContainAnyElementsOf(shouldNotExistFiles);
  }

  @SneakyThrows
  void executeIgnoringMojoFailureException() {
    try {
      underTest.execute();
    } catch (MojoFailureException e) {
      log.info("Expected exception: " + e);
    }
  }

  private static Collection<Arguments> provideReportTypes() {
    return List.of(
        Arguments.of(
            "html", "index.html", List.of("serenity-summary.html", "serenity-summary.json")),
        Arguments.of(
            "single-page-html",
            "serenity-summary.html",
            List.of("index.html", "serenity-summary.json")),
        Arguments.of(
            "json-summary",
            "serenity-summary.json",
            List.of("index.html", "serenity-summary.html")));
  }

  @Tag("de.gematik.test.tiger.common.LongRunnerTest")
  @Test
  @DisplayName(
      "If the property openSerenityReportInBrowser is set, the browser should open with the"
          + " serenity report")
  @SneakyThrows
  void testIfTheBrowserOpensWithSerenityReport() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              underTest.setOpenSerenityReportInBrowser(true);
              underTest.execute();
            });
  }

  @SneakyThrows
  private void prepareReportDir() {
    final var repoRessourceDir =
        Paths.get(getClass().getResource("/serenityReports/fresh").toURI());
    FileUtils.deleteDirectory(reportDir.toFile());
    Files.createDirectories(reportDir);
    FileUtils.copyDirectory(repoRessourceDir.toFile(), reportDir.toFile());
  }
}
