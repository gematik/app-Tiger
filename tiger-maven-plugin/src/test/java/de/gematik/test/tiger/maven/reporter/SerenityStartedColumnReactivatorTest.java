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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SerenityStartedColumnReactivatorTest {

  @Test
  void shouldInjectReactivationScriptIntoHtml() {
    String html =
        """
        <html><head><title>Serenity Reports</title></head><body>
        <script>
          $('#test-results-table').DataTable({
            columnDefs: [
              {
                targets: 4, // Started column
                visible: false
              },
              {
                targets: 7,
                visible: false
              }
            ]
          });
        </script>
        </body></html>
        """;

    String result = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(html);

    assertThat(result)
        .contains("id=\"tiger-started-column-reactivator\"")
        .contains("dataTable.column(4).visible(true, false);")
        .contains("targets: 7,\n        visible: false");
  }

  @Test
  void shouldNotChangeHtmlWhenStartedColumnIsAlreadyVisible() {
    String html =
        """
        <html><head><title>Serenity Reports</title></head><body>
        <script>
          const table = { columnDefs: [ { targets: 4, visible: true } ] };
        </script>
        </body></html>
        """;

    String result = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(html);

    assertThat(result)
        .contains("id=\"tiger-started-column-reactivator\"")
        .contains("<script>\n  const table = { columnDefs: [ { targets: 4, visible: true } ] };");
  }

  @Test
  void shouldNotChangeVisibilityOutsideColumnDefs() {
    String html =
        """
        <html><head><title>Serenity Reports</title></head><body>
        <script>
          const x = { targets: 4, visible: false };
          const table = { columnDefs: [ { targets: 2, visible: false } ] };
        </script>
        </body></html>
        """;

    String result = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(html);

    assertThat(result)
        .contains("const x = { targets: 4, visible: false };")
        .contains("id=\"tiger-started-column-reactivator\"");
  }

  @Test
  void shouldNotInjectScriptTwice() {
    String html =
        """
        <html>
        <head><title>Serenity Reports</title></head>
        <body>
        content
        </body>
        </html>
        """;

    String firstRun = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(html);
    String secondRun = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(firstRun);

    assertThat(secondRun).isEqualTo(firstRun);
  }

  @Test
  void shouldNotInjectScriptIntoNonSerenityHtml() {
    String html =
        """
        <html>
        <head><title>My Report</title></head>
        <body>content</body>
        </html>
        """;

    String result = SerenityStartedColumnReactivator.reactivateStartedColumnInHtml(html);

    assertThat(result).isEqualTo(html);
  }
}
