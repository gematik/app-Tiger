package io.cucumber.core.plugin.report;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class EvidenceRenderer {

  private final HtmlEvidenceRenderer htmlEvidenceRenderer;

  public EvidenceRenderer(HtmlEvidenceRenderer htmlEvidenceRenderer) {
    this.htmlEvidenceRenderer = htmlEvidenceRenderer;
  }

  public String render(EvidenceReport report) {
    try {
      return htmlEvidenceRenderer.render(report);
    } catch (IOException e) {
      log.error("Template processing error for Evidences", e);
      return EvidenceReportJsonConverter.toJson(report);
    }
  }
}


