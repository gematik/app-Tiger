/*
 *  Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.renderer;

import static j2html.TagCreator.*;

import j2html.tags.DomContent;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.val;

/**
 * Renders the "Information" button in the report header and the overlay behind it. Empty metadata
 * renders nothing at all, so reports without metadata stay exactly as they were.
 */
public class RbelReportMetadataRenderer {

  private static final String MODAL_ID = "rbel-report-metadata";
  private static final String YAML_ID = "rbel-report-metadata-yaml";
  private static final String SECTION_CLASSES = "mb-4 test-report-metadata-section";

  private RbelReportMetadataRenderer() {}

  public static DomContent renderButton(RbelReportMetadata metadata) {
    if (metadata.isEmpty()) {
      return span();
    }
    return span()
        .withClass("col-2 my-auto")
        .with(
            a().withClass("btn btn-sm modal-button test-report-metadata-button")
                .withId("test-report-metadata-button")
                .attr("data-bs-target", "#" + MODAL_ID)
                .attr("data-bs-toggle", RbelHtmlRenderer.MODAL)
                .attr("title", "Tiger version, active parsers and configuration")
                .with(
                    span().withClass("icon is-small").with(i().withClass("fas fa-circle-info")),
                    span(" Information")));
  }

  public static DomContent renderModal(RbelReportMetadata metadata) {
    if (metadata.isEmpty()) {
      return span();
    }
    return div()
        .withClass(RbelHtmlRenderer.MODAL)
        .withId(MODAL_ID)
        .attr("role", "dialog")
        .attr("tabindex", "-1")
        .with(
            div()
                .withClass("modal-dialog modal-lg modal-dialog-scrollable")
                .with(div().withClass("modal-content").with(renderHeader(), renderBody(metadata))));
  }

  private static DomContent renderHeader() {
    return div()
        .withClass("modal-header bg-dark")
        .with(
            h5("Report metadata").withClass("modal-title").withStyle("color:#fff;"),
            button()
                .withClass("btn btn-sm btn-outline-light copyToClipboard-button")
                .attr("data-target", YAML_ID)
                .attr("title", "Copy as YAML")
                .with(i().withClass("fa fa-clipboard"), span(" Copy as YAML")),
            button()
                .withClass("btn btn-close btn-close-white")
                .attr("data-bs-dismiss", RbelHtmlRenderer.MODAL)
                .attr("aria-label", "Close"));
  }

  private static DomContent renderBody(RbelReportMetadata metadata) {
    return div()
        .withClass("modal-body")
        .with(
            tableSection("General", generalEntries(metadata)),
            parserSection(metadata),
            tableSection("Configuration", metadata.getConfiguration()),
            // the copy button reads the textContent of this block, see rbel.js
            pre(metadata.toYaml()).withId(YAML_ID).withClass("d-none"));
  }

  private static Map<String, String> generalEntries(RbelReportMetadata metadata) {
    val general = new LinkedHashMap<String, String>();
    general.put("Tiger version", metadata.getTigerVersion());
    general.put("Report created", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
    return general;
  }

  private static DomContent tableSection(String title, Map<String, String> entries) {
    val presentEntries =
        entries.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .toList();
    if (presentEntries.isEmpty()) {
      return span();
    }
    return div()
        .withClass(SECTION_CLASSES)
        .with(
            h6(title).withClass("text-uppercase"),
            table()
                .withClass("table table-sm table-striped rbel-metadata-table")
                .with(
                    tbody()
                        .with(
                            presentEntries.stream()
                                .map(
                                    entry ->
                                        tr().with(
                                                th(entry.getKey())
                                                    .withClass("rbel-metadata-key")
                                                    .attr("scope", "row"),
                                                td(entry.getValue())))
                                .toList())));
  }

  private static DomContent parserSection(RbelReportMetadata metadata) {
    return div()
        .with(
            badgeSection("Active parsers", metadata.getActiveParsers(), "bg-success"),
            badgeSection(
                "Known, but inactive parsers", metadata.getInactiveParsers(), "bg-secondary"));
  }

  private static DomContent badgeSection(String title, List<String> parsers, String colorClass) {
    if (parsers.isEmpty()) {
      return span();
    }
    return div()
        .withClass(SECTION_CLASSES)
        .with(h6(title).withClass("text-uppercase"), div().with(badges(parsers, colorClass)));
  }

  private static List<DomContent> badges(List<String> parsers, String colorClass) {
    return parsers.stream()
        .map(parser -> (DomContent) span(parser).withClass("badge " + colorClass + " me-1"))
        .toList();
  }
}
