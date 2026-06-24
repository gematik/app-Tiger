/*
 *
 * Copyright 2026 gematik GmbH
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
package io.cucumber.core.plugin.report;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.report.ReportDataKeys;
import java.util.ArrayList;
import java.util.List;
import net.thucydides.model.domain.ReportData;
import net.thucydides.model.domain.TestResult;
import net.thucydides.model.domain.TestStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SerenityReportFileManipulatorTest {

  /**
   * TestStep.getReportData() returns a new ArrayList when the internal field is null, so
   * step.getReportData().add(...) has no effect. We must create the list and assign it via
   * reflection to match how Serenity populates the field during JSON deserialization.
   */
  private static TestStep stepWithReportData(
      String description, String reportDataKey, String reportDataValue) {
    TestStep step = TestStep.forStepCalled(description).withResult(TestResult.SUCCESS);
    var dataList = new ArrayList<ReportData>();
    dataList.add(ReportData.withTitle(reportDataKey).andContents(reportDataValue));
    try {
      var field = TestStep.class.getDeclaredField("reportData");
      field.setAccessible(true);
      field.set(step, dataList);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return step;
  }

  @Test
  @DisplayName("Should extract and remove Tiger resolved step description from report data")
  void shouldExtractTigerResolvedStepDescription() {
    TestStep step =
        stepWithReportData(
            "original description",
            ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY,
            "resolved description");

    SerenityReportFileManipulator.replaceTigerResolvedStepDescriptions(step);

    assertThat(step.getDescription()).isEqualTo("resolved description");
    assertThat(
            step.getReportData().stream()
                .filter(
                    d -> d.getTitle().equals(ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY))
                .findAny())
        .isEmpty();
  }

  @Test
  @DisplayName("Should extract and remove multiline docstring from report data")
  void shouldExtractMultilineDocstring() {
    TestStep step =
        stepWithReportData(
            "original",
            ReportDataKeys.COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING,
            "full docstring content");

    SerenityReportFileManipulator.replaceMultilineDocstringDescription(step);

    assertThat(step.getDescription()).isEqualTo("full docstring content");
    assertThat(
            step.getReportData().stream()
                .filter(
                    d ->
                        d.getTitle().equals(ReportDataKeys.COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING))
                .findAny())
        .isEmpty();
  }

  @Test
  @DisplayName("Should not modify step when no custom report data is present")
  void shouldNotModifyStepWhenNoCustomData() {
    TestStep step = TestStep.forStepCalled("keep this").withResult(TestResult.SUCCESS);

    SerenityReportFileManipulator.replaceTigerResolvedStepDescriptions(step);

    assertThat(step.getDescription()).isEqualTo("keep this");
  }

  @Test
  @DisplayName("Should collect steps recursively including children")
  void shouldCollectStepsRecursively() {
    TestStep parent = TestStep.forStepCalled("parent").withResult(TestResult.SUCCESS);
    TestStep child = TestStep.forStepCalled("child").withResult(TestResult.SUCCESS);
    parent.addChildStep(child);

    List<TestStep> children = SerenityReportFileManipulator.collectChildren(parent);

    assertThat(children).hasSize(1);
    assertThat(children.get(0).getDescription()).isEqualTo("child");
  }
}
