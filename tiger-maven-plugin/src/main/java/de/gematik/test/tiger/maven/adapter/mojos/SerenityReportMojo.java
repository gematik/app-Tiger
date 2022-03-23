/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.maven.adapter.mojos;

import java.io.File;
import java.io.IOException;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.serenitybdd.reports.email.SinglePageHtmlReporter;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Generate aggregate XML acceptance test reports. x *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Mojo(name = "generate-serenity-reports", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class SerenityReportMojo extends AbstractMojo {

  /**
   * Serenity report dir
   */
  @Parameter(defaultValue = "${project.build.directory}/site/serenity", required = true)
  public File reportDirectory;

  /**
   * Base directory for requirements.
   */
  @Parameter
  public String requirementsBaseDir;

  private final HtmlAggregateStoryReporter reporter = new HtmlAggregateStoryReporter("default");
  private final SinglePageHtmlReporter singlePageReporter = new SinglePageHtmlReporter();

  @Override
  public void execute() throws MojoExecutionException {
    try {
      generateHtmlStoryReports();
    } catch (final IOException e) {
      throw new MojoExecutionException("Error generating serenity reports", e);
    }
  }

  private void generateHtmlStoryReports() throws IOException {
    if (!reportDirectory.exists()) {
      getLog().warn("Report directory does not exist yet: " + reportDirectory);
      return;
    }
    reporter.setSourceDirectory(reportDirectory);
    reporter.setOutputDirectory(reportDirectory);
    reporter.generateReportsForTestResultsFrom(reportDirectory);

    singlePageReporter.setSourceDirectory(reportDirectory.toPath());
    singlePageReporter.setOutputDirectory(reportDirectory.toPath());
    singlePageReporter.generateReport();
  }
}
