/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.maven.adapter.mojos;

import de.gematik.test.tiger.common.web.TigerBrowserUtil;
import java.io.File;
import java.nio.file.Path;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.serenitybdd.reports.email.SinglePageHtmlReporter;
import net.thucydides.core.reports.ResultChecker;
import net.thucydides.core.reports.TestOutcomes;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import net.thucydides.core.requirements.FileSystemRequirements;
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

  private void generateHtmlStoryReports() throws Exception {
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
