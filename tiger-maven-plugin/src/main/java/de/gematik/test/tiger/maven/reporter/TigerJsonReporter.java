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
package de.gematik.test.tiger.maven.reporter;

import java.nio.file.Path;
import net.serenitybdd.reports.json.JsonSummaryReporter;
import net.thucydides.model.environment.SystemEnvironmentVariables;
import org.apache.maven.plugin.logging.Log;

public class TigerJsonReporter implements TigerReporter {
  private final JsonSummaryReporter reporter;
  private Path generatedReport;

  public TigerJsonReporter(Path reportDirectory) {
    reporter =
        new JsonSummaryReporter(new SystemEnvironmentVariables(), reportDirectory, reportDirectory);
  }

  @Override
  public void logReportUri(Log log) {
    if (generatedReport != null) {
      log.info("  - " + reporter.getDescription() + ": " + generatedReport.toUri());
    }
  }

  @Override
  public void generateReport() {
    generatedReport = reporter.generateReport();
  }
}
