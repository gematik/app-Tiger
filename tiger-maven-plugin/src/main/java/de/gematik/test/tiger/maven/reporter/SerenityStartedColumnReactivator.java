/*
 *
 * Copyright 2021-2026 gematik GmbH
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SerenityStartedColumnReactivator {
  private static final String STARTED_COLUMN_REACTIVATOR_SCRIPT_ID =
      "tiger-started-column-reactivator";
  private static final String SERENITY_REPORT_TITLE_MARKER = "<title>Serenity Reports</title>";
  private static final String SERENITY_REPORT_VERSION_MARKER = "Serenity BDD version";
  private static final String STARTED_COLUMN_REACTIVATOR_SCRIPT =
      """
      <script id="tiger-started-column-reactivator">
      (function () {
        function reactivateStartedColumn() {
          if (!window.jQuery || !window.jQuery.fn || !window.jQuery.fn.dataTable) {
            return;
          }
          var $ = window.jQuery;
          var selector = '#scenario-results';
          var table = document.querySelector(selector);
          if (!table || !$.fn.dataTable.isDataTable(table)) {
            return;
          }
          try {
            var dataTable = $(table).DataTable();
            dataTable.column(4).visible(true, false);
            dataTable.columns.adjust().draw(false);
          } catch (e) {
            // best-effort in report runtime
          }
        }

        function registerReactivationHooks() {
          if (!window.jQuery || !window.jQuery.fn || !window.jQuery.fn.dataTable) {
            return;
          }
          var $ = window.jQuery;
          $(document).on('init.dt', function (event, settings) {
            if (settings && settings.nTable && settings.nTable.id === 'scenario-results') {
              reactivateStartedColumn();
            }
          });
        }

        registerReactivationHooks();
        reactivateStartedColumn();
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', reactivateStartedColumn);
        }
        window.addEventListener('load', reactivateStartedColumn);
      })();
      </script>
      """;

  private SerenityStartedColumnReactivator() {}

  public static void reactivateInDirectory(Path outputDirectory) {
    try (Stream<Path> files = Files.walk(outputDirectory)) {
      files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".html"))
          .forEach(SerenityStartedColumnReactivator::reactivateInFile);
    } catch (IOException | UncheckedIOException | SecurityException e) {
      log.trace(
          "Failed to walk directory {} for started-column reactivation. Ignoring exception: {}",
          outputDirectory,
          e.toString());
      // best-effort post-processing: leave reports unchanged on any issue
    }
  }

  public static String reactivateStartedColumnInHtml(String html) {
    if (!isSerenityReportHtml(html)) {
      return html;
    }
    return injectStartedColumnReactivatorScript(html);
  }

  private static boolean isSerenityReportHtml(String html) {
    return html.contains(SERENITY_REPORT_TITLE_MARKER)
        || html.contains(SERENITY_REPORT_VERSION_MARKER);
  }

  private static String injectStartedColumnReactivatorScript(String html) {
    if (html.contains("id=\"" + STARTED_COLUMN_REACTIVATOR_SCRIPT_ID + "\"")) {
      return html;
    }
    int bodyEnd = html.lastIndexOf("</body>");
    if (bodyEnd >= 0) {
      return html.substring(0, bodyEnd)
          + STARTED_COLUMN_REACTIVATOR_SCRIPT
          + "\n"
          + html.substring(bodyEnd);
    }
    return html + "\n" + STARTED_COLUMN_REACTIVATOR_SCRIPT;
  }

  private static void reactivateInFile(Path htmlFile) {
    try {
      String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
      String updated = reactivateStartedColumnInHtml(content);
      if (!content.equals(updated)) {
        Files.writeString(htmlFile, updated, StandardCharsets.UTF_8);
      }
    } catch (IOException | SecurityException e) {
      log.trace(
          "Failed to reactivate started column in file {}. Ignoring exception: {}",
          htmlFile,
          e.toString());
      // best-effort post-processing: leave this file unchanged on any issue
    }
  }
}
