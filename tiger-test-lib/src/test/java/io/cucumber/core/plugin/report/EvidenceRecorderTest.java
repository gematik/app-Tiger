package io.cucumber.core.plugin.report;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.cucumber.core.plugin.report.Evidence.Type;
import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.core.plugin.report.EvidenceReport.Step;
import java.net.URI;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceRecorderTest {

  private final EvidenceRecorder underTest = EvidenceRecorderFactory.getEvidenceRecorder();

  @BeforeEach
  void setUp() {
    underTest.reset();
  }

  @Test
  @DisplayName("Evidences should be added to the currently active step")
  @SneakyThrows
  void getEvidenceReport_EvidencesShouldBeAddedToTheCurrentlyActiveStep() {
    // Arrange
    underTest.openStepContext(new ReportStepConfiguration("Mopsgeschwader"));
    underTest.recordEvidence(new Evidence(Type.INFO, "Title11", "some fancy details1"));
    underTest.openStepContext(new ReportStepConfiguration("Hundekuchen"));
    underTest.recordEvidence(new Evidence(Type.INFO, "Title21"));
    underTest.recordEvidence(new Evidence(Type.INFO, "Title22", "some fancy details3"));

    // Act
    var result =
        underTest.getEvidenceReportForScenario(
            new ReportContext("brave new world", URI.create("file://somewhat.feature")));

    // Assert
    assertSoftly(
        soft -> {
          soft.assertThat(result.getContext())
              .isEqualTo(
                  new ReportContext("brave new world", URI.create("file://somewhat.feature")));
          soft.assertThat(result.getSteps())
              .containsExactly(
                  new Step(
                      "Mopsgeschwader",
                      List.of(new Evidence(Type.INFO, "Title11", "some fancy details1"))),
                  new Step(
                      "Hundekuchen",
                      List.of(
                          new Evidence(Type.INFO, "Title21"),
                          new Evidence(Type.INFO, "Title22", "some fancy details3"))));
        });
  }

  @Test
  @DisplayName("empty steps should be fine as well")
  @SneakyThrows
  void getEvidenceReport_EmptyStepsShouldBeFineAsWell() {
    // Arrange
    underTest.openStepContext(new ReportStepConfiguration("Mopsgeschwader"));

    // Act
    var result =
        underTest.getEvidenceReportForScenario(
            new ReportContext("brave new world", URI.create("file://somewhat.feature")));

    // Assert
    org.assertj.core.api.Assertions.assertThat(result.getSteps())
        .contains(new Step("Mopsgeschwader", emptyList()));
  }

  @Test
  @DisplayName(
      "It should not be possible to add entries without a prior opened step to get early feedback")
  @SneakyThrows
  void
      getEvidenceReport_ItShouldNotBePossibleToAddEntriesWithoutAPriorOpenedStepToGetEarlyFeedback() {
    // Arrange
    final Evidence evidence = new Evidence(Type.INFO, "Title21", "some fancy details2");

    // Assert
    assertThatThrownBy(
            () ->
                // Act
                underTest.recordEvidence(evidence))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No step opened in EvidenceRecorder yet");
  }

  @Test
  @DisplayName("should return nothing if no step was recordet yet")
  @SneakyThrows
  void getCurrentStep_ShouldReturnNothingIfNoStepWasRecordetYet() {
    // Arrange

    // Act
    var result = underTest.getCurrentStep();

    // Assert
    org.assertj.core.api.Assertions.assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should serve the current step if elements are in there")
  @SneakyThrows
  void getCurrentStep_ShouldServeTheCurrentStepIfElementsAreInThere() {
    // Arrange
    underTest.openStepContext(new ReportStepConfiguration("Mopsgeschwader"));
    underTest.openStepContext(new ReportStepConfiguration("Hundekuchen"));

    // Act
    var result = underTest.getCurrentStep();

    // Assert
    assertThat(result)
        .get()
        .isInstanceOf(EvidenceReport.Step.class)
        .extracting("name")
        .isEqualTo("Hundekuchen");
  }
}
