/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import de.gematik.test.tiger.common.web.TigerBrowserUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.serenitybdd.reports.email.SinglePageHtmlReporter;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import net.thucydides.model.domain.ReportData;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.reports.OutcomeFormat;
import net.thucydides.model.reports.ResultChecker;
import net.thucydides.model.reports.TestOutcomeLoader;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.reports.json.JSONTestOutcomeReporter;
import net.thucydides.model.requirements.FileSystemRequirements;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Generate aggregate XML acceptance test reports. x * */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "generate-serenity-reports", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class TigerSerenityReportMojo extends AbstractMojo {

  /** Serenity report dir */
  @Parameter(defaultValue = "${project.build.directory}/site/serenity", required = true)
  public File reportDirectory;

  /** Base directory for requirements. */
  @Parameter(defaultValue = "src/test/resources/features", required = true)
  public String requirementsBaseDir;

  /** Opens browser with serenity report files if set to true. */
  @Parameter(defaultValue = "false", required = false)
  public boolean openSerenityReportInBrowser;

  @Override
  public void execute() throws MojoExecutionException {
    try {
      generateHtmlStoryReports();
    } catch (final Exception e) {
      throw new MojoExecutionException("Error generating serenity reports", e);
    }
  }

  private void generateHtmlStoryReports() throws IOException {
    if (!reportDirectory.exists()) {
      getLog().warn("Report directory does not exist yet: " + reportDirectory);
      return;
    }
    TestOutcomes testOutcomesToManipulate =
        TestOutcomeLoader.loadTestOutcomes()
            .inFormat(OutcomeFormat.JSON)
            .from(reportDirectory)
            .withRequirementsTags();

    var outcomesReporter = new JSONTestOutcomeReporter();
    outcomesReporter.setOutputDirectory(reportDirectory);

    for (TestOutcome outcome : testOutcomesToManipulate.getOutcomes()) {
      List<TestStep> allSteps = collectSteps(outcome);
      replaceTigerResolvedStepDescriptions(allSteps);
      outcomesReporter.generateReportFor(outcome);
    }

    HtmlAggregateStoryReporter reporter =
        new HtmlAggregateStoryReporter("default", new FileSystemRequirements(requirementsBaseDir));
    reporter.setSourceDirectory(reportDirectory);
    reporter.setOutputDirectory(reportDirectory);
    reporter.setGenerateTestOutcomeReports();
    TestOutcomes outcomes = reporter.generateReportsForTestResultsFrom(reportDirectory);
    new ResultChecker(reportDirectory).checkTestResults(outcomes);

    SinglePageHtmlReporter singlePageReporter = new SinglePageHtmlReporter();
    singlePageReporter.setSourceDirectory(reportDirectory.toPath());
    singlePageReporter.setOutputDirectory(reportDirectory.toPath());
    Path generatedReport = singlePageReporter.generateReport();
    if (openSerenityReportInBrowser) {
      TigerBrowserUtil.openUrlInBrowser(
          reportDirectory.toPath() + "\\index.html", "browser for serenity report");
    }
    logFullHtmlReportUri();
    getLog().info("  - " + singlePageReporter.getDescription() + ": " + generatedReport.toUri());
  }

  private List<TestStep> collectSteps(TestOutcome outcome) {
    var result = new ArrayList<TestStep>();
    outcome
        .getTestSteps()
        .forEach(
            step -> {
              result.add(step);
              result.addAll(collectChildren(step));
            });
    return result;
  }

  private List<TestStep> collectChildren(TestStep step) {
    var result = new ArrayList<TestStep>();
    var children = step.getChildren();
    result.addAll(children);
    children.forEach(child -> result.addAll(collectChildren(child)));
    return result;
  }

  private void replaceTigerResolvedStepDescriptions(List<TestStep> steps) {
    steps.forEach(step -> getTigerResolvedStepDescription(step).ifPresent(step::setDescription));
  }

  private Optional<String> getTigerResolvedStepDescription(TestStep step) {
    var reportDataWithDescription =
        step.getReportData().stream()
            .filter(data -> data.getTitle().equals("Tiger Resolved Step Description"))
            .findAny();
    reportDataWithDescription.ifPresent(reportData -> step.getReportData().remove(reportData));
    return reportDataWithDescription
        .map(ReportData::getContents)
        .map(StringEscapeUtils::unescapeJava);
  }

  public void logFullHtmlReportUri() {
    Path index = getReportDirectory().toPath().resolve("index.html");
    getLog().info("  - Full Report: %s".formatted(index.toUri()));
  }
}
