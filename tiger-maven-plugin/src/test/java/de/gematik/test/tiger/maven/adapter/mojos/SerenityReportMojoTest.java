/*
 * Copyright (c) 2022 gematik GmbH
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
package de.gematik.test.tiger.maven.adapter.mojos;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.readString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SerenityReportMojoTest {

  @Mock
  private Log log;

  @TempDir
  private Path reportDir;

  private SerenityReportMojo underTest;

  @BeforeEach
  void setUp() {
    underTest = new SerenityReportMojo();
    underTest.setLog(log);
    underTest.setReportDirectory(reportDir.toFile());
  }

  @Test
  @DisplayName("If the report directory does not exist, a warning should be shown")
  @SneakyThrows
  void execute_IfTheReportDirectoryDoesNotExistAWarningShouldBeShown() {
    // Preparation
    Files.delete(reportDir);

    // Execution
    underTest.execute();

    // Assertion
    verify(log).warn("Report directory does not exist yet: " + reportDir);
  }

  @Test
  @DisplayName("Sollte alle relevanten Reporttypen mit Informationen zu Fehlern enthalten")
  @SneakyThrows
  void execute_SollteAlleRelevantenReporttypenMitInformationenZuFehlernEnthalten() {
    // Preparation
    prepareReportDir();

    // Execution
    underTest.execute();

    // Assertion
    assertAll(
        () -> assertThat(readAllLines(reportDir.resolve("results.csv")),
            containsInAnyOrder(
                startsWith("\"Story\",\"Title\",\"Result\","),
                startsWith("\"Test Tiger BDD\",\"Simple first test\",\"SUCCESS\","),
                startsWith("\"Test Tiger BDD\",\"Simple second test\",\"FAILURE\","),
                startsWith("\"Test Tiger BDD\",\"Simple third test\",\"SUCCESS\",")
            )),
        () -> assertThat(readString(reportDir.resolve("serenity-summary.html")),
            containsString("2 passing tests")),
        () -> assertThat(readString(reportDir.resolve("serenity-summary.html")),
            containsString("1 failing test")),
        () -> assertThat(readString(reportDir.resolve("serenity-summary.html")),
            containsString(
                "Assertion error: Expecting actual:  &quot;jetty-dir.css&quot;to match pattern:  &quot;mööööp&quot;")),
        () -> assertTrue(Files.exists(reportDir.resolve("index.html")), "index.html exists")
    );
  }

  @SneakyThrows
  private void prepareReportDir() {
    final var repoRessourceDir = Paths.get(
        getClass().getResource("/serenetyReports/fresh").toURI());
    FileUtils.copyDirectory(repoRessourceDir.toFile(), reportDir.toFile());
  }
}