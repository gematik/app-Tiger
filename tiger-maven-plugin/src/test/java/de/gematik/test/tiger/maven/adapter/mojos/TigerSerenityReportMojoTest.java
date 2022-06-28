/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
class TigerSerenityReportMojoTest {

  @Mock
  private Log log;

  @TempDir
  private Path reportDir;

  private TigerSerenityReportMojo underTest;

  @BeforeEach
  void setUp() {
    underTest = new TigerSerenityReportMojo();
    underTest.setLog(log);
    underTest.setReportDirectory(reportDir.toFile());
    underTest.setRequirementsBaseDir("src/test/resources");
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
