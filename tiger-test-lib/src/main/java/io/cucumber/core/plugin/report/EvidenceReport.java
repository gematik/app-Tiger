/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
public class EvidenceReport {

  @Value
  @AllArgsConstructor
  public static class Step {

    String name;

    public Step(String name) {
      this.name = name;
      evidenceEntries = Collections.unmodifiableList(new ArrayList<>());
    }

    @With(AccessLevel.PRIVATE)
    List<Evidence> evidenceEntries;

    public Step addEntry(Evidence stepEvidenceToAdd) {
      return withEvidenceEntries(
          Stream.concat(evidenceEntries.stream(), Stream.of(stepEvidenceToAdd)).toList());
    }
  }

  @Value
  public static class ReportContext {

    String scenario;
    URI feature;
  }

  ReportContext context;

  List<Step> steps;
}
