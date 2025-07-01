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
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;

public class ReporterGenerator {
  TigerHtmlReporter htmlReporter;
  TigerSinglePageReporter singlePageReporter;
  TigerJsonReporter jsonReporter;
  List<TigerReporter> activeReporters = new ArrayList<>();
  Log log;

  public ReporterGenerator(
      List<String> reportTypes, Path reportDirectory, Path requirementsBaseDir, Log log) {
    this.log = log;
    if (reportTypes.contains("html")) {
      htmlReporter = new TigerHtmlReporter(reportDirectory, requirementsBaseDir);
      activeReporters.add(htmlReporter);
    }
    if (reportTypes.contains("single-page-html")) {
      singlePageReporter = new TigerSinglePageReporter(reportDirectory);
      activeReporters.add(singlePageReporter);
    }
    if (reportTypes.contains("json-summary")) {
      jsonReporter = new TigerJsonReporter(reportDirectory);
      activeReporters.add(jsonReporter);
    }
  }

  public void generateReports() {
    activeReporters.forEach(
        reporter -> {
          reporter.generateReport();
          reporter.logReportUri(log);
        });
  }
}
