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

import java.io.File;
import net.serenitybdd.maven.plugins.SerenityCheckMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "check-report", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class TigerSerenityCheckMojo extends SerenityCheckMojo {

  /** Serenity report dir */
  @Parameter(defaultValue = "${project.build.directory}/site/serenity", required = true)
  public File reportDirectory;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if ("pom".equals(project.getPackaging())) {
      getLog().info("Skipping execution for POM packaging");
    } else if (!reportDirectory.exists()) {
      // The original serenity check plugin always aggregates reports and always checks them.
      // But our custom tiger reporter skips the aggregation if the reports folder is not existing.
      // Therefore, we also skip the report checking, otherwise we would break builds where tests
      // are
      // skipped
      getLog().info("Report directory does not exist. Skipping report check");
    } else {
      executeSerenityCheckMojo();
    }
  }

  /**
   * As a separate method, to be able to override it in the unit test
   *
   * @throws MojoExecutionException
   * @throws MojoFailureException
   */
  protected void executeSerenityCheckMojo() throws MojoExecutionException, MojoFailureException {
    super.execute();
  }
}
