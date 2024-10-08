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
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.serenitybdd.reports.email.SinglePageHtmlReporter;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import net.thucydides.model.reports.ResultChecker;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.requirements.FileSystemRequirements;
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
    getLog().info("  - " + singlePageReporter.getDescription() + ": " + generatedReport.toUri());
  }
}
