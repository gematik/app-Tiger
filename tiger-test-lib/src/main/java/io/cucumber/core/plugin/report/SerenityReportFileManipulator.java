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

import de.gematik.test.tiger.common.report.ReportDataKeys;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.thucydides.model.domain.ReportData;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.domain.TestStep;
import net.thucydides.model.reports.OutcomeFormat;
import net.thucydides.model.reports.TestOutcomeLoader;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.reports.json.JSONTestOutcomeReporter;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Shared utility for manipulating Serenity JSON test outcome report files. This class provides the
 * common logic used by both {@code TigerSerenityReportMojo} (final report generation at the end of
 * a test run) and {@link IntermediateReportGenerator} (continuous report generation after each
 * feature file).
 *
 * <p>The two main manipulations are:
 *
 * <ul>
 *   <li>Replacing step descriptions with Tiger-resolved values (showing actual resolved placeholder
 *       values)
 *   <li>Expanding multiline docstring arguments to their complete unresolved form
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SerenityReportFileManipulator {

  /**
   * Applies both multiline docstring expansion and Tiger resolved step description replacement to
   * all test outcome JSON files in the given directory in a single load/save pass.
   *
   * @param reportDirectory the directory containing the Serenity JSON outcome files
   * @throws IOException if reading or writing the report files fails
   */
  public static void applyAllStepManipulations(File reportDirectory) throws IOException {
    modifyExistingReportFiles(
        reportDirectory,
        step -> {
          replaceMultilineDocstringDescription(step);
          replaceTigerResolvedStepDescriptions(step);
        });
  }

  /**
   * Applies Tiger resolved step descriptions to all test outcome JSON files in the given directory.
   * Steps that have a {@link ReportDataKeys#TIGER_RESOLVED_STEP_DESCRIPTION_KEY} report data entry
   * will have their description replaced with the resolved value.
   *
   * @param reportDirectory the directory containing the Serenity JSON outcome files
   * @throws IOException if reading or writing the report files fails
   */
  public static void modifyStepDescriptionsWithTigerResolvedValues(File reportDirectory)
      throws IOException {
    modifyExistingReportFiles(
        reportDirectory, SerenityReportFileManipulator::replaceTigerResolvedStepDescriptions);
  }

  /**
   * Expands multiline docstring arguments in all test outcome JSON files in the given directory.
   * Steps that have a {@link ReportDataKeys#COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING} report data
   * entry will have their description replaced with the full unresolved docstring.
   *
   * @param reportDirectory the directory containing the Serenity JSON outcome files
   * @throws IOException if reading or writing the report files fails
   */
  public static void includeFullUnresolvedDocstringArguments(File reportDirectory)
      throws IOException {
    modifyExistingReportFiles(
        reportDirectory, SerenityReportFileManipulator::replaceMultilineDocstringDescription);
  }

  /**
   * Loads all test outcomes from the given directory, applies the modifier to each step, and writes
   * them back.
   *
   * @param reportDirectory the directory containing the Serenity JSON outcome files
   * @param testStepModifier the modification to apply to each test step
   * @throws IOException if reading or writing the report files fails
   */
  public static void modifyExistingReportFiles(
      File reportDirectory, Consumer<TestStep> testStepModifier) throws IOException {
    TestOutcomes testOutcomesToManipulate =
        TestOutcomeLoader.loadTestOutcomes()
            .inFormat(OutcomeFormat.JSON)
            .from(reportDirectory)
            .withRequirementsTags();

    var outcomesReporter = new JSONTestOutcomeReporter();
    outcomesReporter.setOutputDirectory(reportDirectory);

    for (TestOutcome outcome : testOutcomesToManipulate.getOutcomes()) {
      List<TestStep> allSteps = collectSteps(outcome);
      allSteps.forEach(testStepModifier);
      outcomesReporter.generateReportFor(outcome);
    }
  }

  static List<TestStep> collectSteps(TestOutcome outcome) {
    var result = new ArrayList<TestStep>();
    outcome
        .getTestSteps()
        .forEach(
            step -> {
              result.add(step);
              result.addAll(collectChildren(step));
            });
    return result;
  }

  static List<TestStep> collectChildren(TestStep step) {
    var result = new ArrayList<TestStep>(step.getChildren());
    step.getChildren().forEach(child -> result.addAll(collectChildren(child)));
    return result;
  }

  static void replaceTigerResolvedStepDescriptions(TestStep step) {
    extractCustomReportData(step, ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY)
        .ifPresent(step::setDescription);
  }

  static void replaceMultilineDocstringDescription(TestStep step) {
    extractCustomReportData(step, ReportDataKeys.COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING)
        .ifPresent(step::setDescription);
  }

  /**
   * Extracts and removes custom report data from a step. Beware of side effect: it removes the
   * given data from the original object.
   */
  static Optional<String> extractCustomReportData(TestStep step, String customKey) {
    var reportDataWithDescription =
        step.getReportData().stream().filter(data -> data.getTitle().equals(customKey)).findAny();
    reportDataWithDescription.ifPresent(reportData -> step.getReportData().remove(reportData));
    return reportDataWithDescription
        .map(ReportData::getContents)
        .map(StringEscapeUtils::unescapeJava);
  }
}
