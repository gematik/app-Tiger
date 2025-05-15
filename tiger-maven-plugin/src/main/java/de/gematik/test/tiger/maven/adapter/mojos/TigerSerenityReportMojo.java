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
 *
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import de.gematik.test.tiger.common.exceptions.TigerOsException;
import de.gematik.test.tiger.common.report.ReportDataKeys;
import de.gematik.test.tiger.common.web.TigerBrowserUtil;
import de.gematik.test.tiger.maven.reporter.ReporterGenerator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.thucydides.model.domain.ReportData;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.reports.OutcomeFormat;
import net.thucydides.model.reports.TestOutcomeLoader;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.reports.json.JSONTestOutcomeReporter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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

  /**
   * A comma separated list of report types to be generated. Per default all report types: html,
   * single-page-html and json-summary are generated.
   */
  @Parameter(defaultValue = "html,single-page-html,json-summary", required = true)
  public List<String> reports = new ArrayList<>();

  /** Base directory for requirements. */
  @Parameter(defaultValue = "src/test/resources/features", required = true)
  public String requirementsBaseDir;

  /** Opens browser with serenity report files if set to true. */
  @Parameter(defaultValue = "false", required = false)
  public boolean openSerenityReportInBrowser;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      generateReports();

    } catch (MojoFailureException e) {
      throw e;
    } catch (final Exception e) {
      throw new MojoExecutionException("Error generating serenity reports", e);
    }
  }

  private void generateReports() throws IOException, MojoFailureException {
    if (!reportDirectory.exists()) {
      getLog().warn("Report directory does not exist yet: " + reportDirectory);
      return;
    }

    includeFullUnresolvedDocstringArguments();
    backupJsonReportFiles();
    modifyStepDescriptionsWithTigerResolvedValues();

    var reportGenerator =
        new ReporterGenerator(
            reports, reportDirectory.toPath(), Path.of(requirementsBaseDir), getLog());
    reportGenerator.generateReports();

    if (openSerenityReportInBrowser) {
      TigerBrowserUtil.openUrlInBrowser(
          reportDirectory.toPath() + "\\index.html", "browser for serenity report");
    }

    reportGenerator.checkResults();
  }

  private void includeFullUnresolvedDocstringArguments() throws IOException {
    modifyExistingReportFiles(this::replaceMultilineDocstringDescription);
  }

  private void backupJsonReportFiles() throws IOException {
    Path unresolvedFolder = Path.of(reportDirectory.getAbsolutePath(), "unresolvedReports");
    if (unresolvedFolder.toFile().exists()) {
      FileUtils.deleteDirectory(unresolvedFolder.toFile());
    }
    Files.createDirectories(Path.of(reportDirectory.getAbsolutePath(), "unresolvedReports"));
    try (Stream<Path> files = Files.list(reportDirectory.toPath())) {
      files
          .filter(path -> path.toString().endsWith(".json"))
          .forEach(
              path -> {
                try {
                  Files.copy(
                      path,
                      unresolvedFolder.resolve(path.getFileName()),
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                  throw new TigerOsException(
                      "Unable to copy JSON report file " + path.toFile().getAbsolutePath(), e);
                }
              });
    }
  }

  private void modifyStepDescriptionsWithTigerResolvedValues() throws IOException {
    modifyExistingReportFiles(this::replaceTigerResolvedStepDescriptions);
  }

  private void modifyExistingReportFiles(Consumer<TestStep> testStepModifier) throws IOException {
    TestOutcomes testOutcomesToManipulate =
        TestOutcomeLoader.loadTestOutcomes()
            .inFormat(OutcomeFormat.JSON)
            .from(reportDirectory)
            .withRequirementsTags();

    var outcomesReporter = new JSONTestOutcomeReporter();
    outcomesReporter.setOutputDirectory(reportDirectory);

    for (TestOutcome outcome : testOutcomesToManipulate.getOutcomes()) {
      List<TestStep> allSteps = collectSteps(outcome);
      allSteps.forEach(testStepModifier);
      outcomesReporter.generateReportFor(outcome);
    }
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

  private void replaceTigerResolvedStepDescriptions(TestStep step) {
    getTigerResolvedStepDescription(step).ifPresent(step::setDescription);
  }

  private void replaceMultilineDocstringDescription(TestStep step) {
    getCompleteUnresolvedMultilineDocstring(step).ifPresent(step::setDescription);
  }

  private Optional<String> getTigerResolvedStepDescription(TestStep step) {
    return extractCustomReportData(step, ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY);
  }

  private Optional<String> getCompleteUnresolvedMultilineDocstring(TestStep step) {
    return extractCustomReportData(step, ReportDataKeys.COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING);
  }

  /** beware of side effect. It removes the given data from the original object. */
  private Optional<String> extractCustomReportData(TestStep step, String customKey) {
    var reportDataWithDescription =
        step.getReportData().stream().filter(data -> data.getTitle().equals(customKey)).findAny();
    reportDataWithDescription.ifPresent(reportData -> step.getReportData().remove(reportData));
    return reportDataWithDescription
        .map(ReportData::getContents)
        .map(StringEscapeUtils::unescapeJava);
  }
}
