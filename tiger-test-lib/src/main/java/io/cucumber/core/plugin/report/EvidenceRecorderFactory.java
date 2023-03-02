package io.cucumber.core.plugin.report;

public class EvidenceRecorderFactory {

  private EvidenceRecorderFactory() {
    // no reason for an instance yet
  }

  private static final EvidenceRecorder context = new EvidenceRecorder();

  public static EvidenceRecorder getEvidenceRecorder() {
    return context;
  }
}
