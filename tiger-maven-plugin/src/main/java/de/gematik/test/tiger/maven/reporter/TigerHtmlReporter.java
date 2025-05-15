/*
 * Copyright 2025 gematik GmbH
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
 */

package de.gematik.test.tiger.maven.reporter;

import java.nio.file.Path;
import java.util.Optional;
import lombok.SneakyThrows;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import net.thucydides.model.domain.TestResult;
import net.thucydides.model.reports.ResultChecker;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.requirements.FileSystemRequirements;
import org.apache.maven.plugin.logging.Log;

public class TigerHtmlReporter implements TigerReporter {

  private final HtmlAggregateStoryReporter reporter;
  private TestResult testResult;

  public TigerHtmlReporter(Path reportDirectory, Path requirementsBaseDir) {
    reporter =
        new HtmlAggregateStoryReporter(
            "default", new FileSystemRequirements(requirementsBaseDir.toString()));
    reporter.setSourceDirectory(reportDirectory.toFile());
    reporter.setOutputDirectory(reportDirectory.toFile());
    reporter.setGenerateTestOutcomeReports();
  }

  @Override
  public void logReportUri(Log log) {
    Path index = reporter.getOutputDirectory().toPath().resolve("index.html");
    log.info("  - Full Report: %s".formatted(index.toUri()));
  }

  @Override
  @SneakyThrows
  public void generateReport() {
    TestOutcomes outcomes =
        reporter.generateReportsForTestResultsFrom(reporter.getSourceDirectory());
    testResult = new ResultChecker(reporter.getOutputDirectory()).checkTestResults(outcomes);
  }

  public Optional<TestResult> getTestResult() {
    return Optional.ofNullable(testResult);
  }
}
