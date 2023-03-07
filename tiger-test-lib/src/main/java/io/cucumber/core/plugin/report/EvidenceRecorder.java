package io.cucumber.core.plugin.report;


import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.core.plugin.report.EvidenceReport.Step;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

 public class EvidenceRecorder {

   EvidenceRecorder() {
     reset();
   }

   private List<Step> steps;


   void reset() {
     steps = new ArrayList<>();
   }

   void openStepContext(ReportStepConfiguration stepConfiguration) {
     steps.add(new Step(stepConfiguration.getStepName()));
   }

   public void recordEvidence(Evidence stepEvidence) {
     if (steps.isEmpty()) {
       throw new IllegalStateException("No step opened in EvidenceRecorder yet");
     }
     final int lastIndex = steps.size() - 1;
     var lastReportStep = steps.get(lastIndex);
     steps.set(lastIndex, lastReportStep.addEntry(stepEvidence));
   }

   EvidenceReport getEvidenceReportForScenario(ReportContext reportContext) {
     return new EvidenceReport(reportContext, Collections.unmodifiableList(steps));
   }

   Optional<Step> getCurrentStep() {
     if (steps.isEmpty()) {
       return Optional.empty();
     } else {
       return Optional.of(steps.get(steps.size() - 1));
     }
   }
 }
