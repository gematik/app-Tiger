/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceRecorderFactory {

  private static final EvidenceRecorder context = new EvidenceRecorder();

  public static EvidenceRecorder getEvidenceRecorder() {
    return context;
  }
}
