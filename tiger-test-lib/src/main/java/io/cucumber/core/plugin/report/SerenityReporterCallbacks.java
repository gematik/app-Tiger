/*
 * Copyright 2024 gematik GmbH
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
 */

package io.cucumber.core.plugin.report;

import static org.awaitility.Awaitility.await;

import com.google.common.collect.Streams;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.exceptions.TigerOsException;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import de.gematik.test.tiger.testenvmgr.env.FeatureUpdate;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.StepUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.core.plugin.FeatureFileLoader;
import io.cucumber.core.plugin.ScenarioContextDelegate;
import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.core.runner.TestCaseDelegate;
import io.cucumber.messages.types.*;
import io.cucumber.plugin.event.Event;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestSourceRead;
import io.cucumber.plugin.event.TestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class SerenityReporterCallbacks {

  public static final String TARGET_DIR = "target";
  private static final TigerTypedConfigurationKey<Integer> MAX_STEP_DESCRIPTION_DISPLAY_LENGTH =
      new TigerTypedConfigurationKey<>(
          "tiger.lib.maxStepDescriptionDisplayLengthOnWebUi", Integer.class, 300);

  private static final Object startupMutex = new Object();
  private static RuntimeException tigerStartupFailedException;
  @Getter @Setter private static boolean pauseMode;

  @SuppressWarnings("java:S5852")
  private final Pattern showSteps =
      Pattern.compile(
          ".*TGR (zeige|show) ([\\w|üß ]*)(Banner|banner|text|Text) \"(.*)\""); // NOSONAR

  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
  private final EvidenceRecorder evidenceRecorder = EvidenceRecorderFactory.getEvidenceRecorder();
  private final EvidenceRenderer evidenceRenderer =
      new EvidenceRenderer(new HtmlEvidenceRenderer());
  FeatureFileLoader featureLoader = new FeatureFileLoader();
  @Getter private int currentScenarioDataVariantIndex = -1;
  private String currentScenarioID = "";
  private int currentStepIndex = -1;

  /** number of passed scenarios / scenario data variants. */
  @Getter private int scPassed = 0;

  /** number of failed scenarios / scenario data variants. */
  @Getter private int scFailed = 0;

  private final FeatureExecutionMonitor featureExecutionMonitor = new FeatureExecutionMonitor();

  @NotNull
  private static Path getEvidenceDir() throws IOException {
    final Path parentDir = Path.of(TARGET_DIR, "evidences");
    if (Files.notExists(parentDir)) {
      Files.createDirectories(parentDir);
    }
    return parentDir;
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test source read
  //
  public void handleTestSourceRead(Event event) {
    featureLoader.addTestSourceReadEvent((TestSourceRead) event);
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test run start
  //
  @SuppressWarnings("java:S1172")
  public void handleTestRunStarted(Event ignoredEvent, ScenarioContextDelegate ignoredContext) {
    synchronized (startupMutex) {
      if (TigerDirector.isInitialized()) {
        return;
      }
      showTigerVersion();
      initializeTiger();
      TigerDirector.assertThatTigerIsInitialized();
      shouldAbortTestExecution();
      featureExecutionMonitor.startTestRun();
    }
  }

  @SuppressWarnings("java:S1172")
  public void handleTestRunFinished(
      TestRunFinished ignoredEvent, ScenarioContextDelegate ignoredContext) {
    featureExecutionMonitor.stopTestRun();
  }

  private void showTigerVersion() {
    log.info(
        Ansi.colorize(
            "Starting Tiger version " + getTigerVersionString(), RbelAnsiColors.GREEN_BRIGHT));
  }

  private String getTigerVersionString() {
    try {
      Properties p = new Properties();
      p.load(SerenityReporterCallbacks.class.getResourceAsStream("/build.properties"));
      String version = p.getProperty("tiger.version");
      if (version.equals("${project.version}")) {
        version = "UNKNOWN";
      }
      return version + "-" + p.getProperty("tiger.build.timestamp");
    } catch (RuntimeException | IOException ignored) {
      return "UNKNOWN";
    }
  }

  private synchronized void initializeTiger() {
    if (tigerStartupFailedException != null) {
      return;
    }
    try {
      TigerDirector.registerShutdownHook();
      TigerDirector.start();
    } catch (RuntimeException rte) {
      tigerStartupFailedException = rte;
      throw tigerStartupFailedException;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test case start
  //
  public void handleTestCaseStarted(
      TestCaseStarted testCaseStartedEvent, ScenarioContextDelegate context) {
    shouldAbortTestExecution();

    Optional<Feature> currentFeature = featureFrom(context.currentFeaturePath());

    if (StringUtils.isEmpty(currentScenarioID)) {
      currentScenarioID = context.getCurrentScenarioId();
    }
    if (context.getCurrentScenarioId() != null
        && !context.getCurrentScenarioId().equals(currentScenarioID)) {
      currentScenarioDataVariantIndex = -1;
      currentScenarioID = context.getCurrentScenarioId();
    }
    // TGR
    if (context.isAScenarioOutline()) {
      currentScenarioDataVariantIndex =
          extractScenarioDataVariantIndex(testCaseStartedEvent, context).orElse(-1);
    } else {
      currentScenarioDataVariantIndex = -1;
      currentScenarioID = context.getCurrentScenarioId();
    }
    currentStepIndex = 0;

    boolean isDryRun = TestCaseDelegate.of(testCaseStartedEvent.getTestCase()).isDryRun();
    currentFeature.ifPresent(
        feature -> informWorkflowUiAboutCurrentScenario(feature, context, isDryRun));
    evidenceRecorder.reset();
    featureExecutionMonitor.startTestCase(testCaseStartedEvent);
  }

  private Optional<Integer> extractScenarioDataVariantIndex(
      TestCaseStarted event, ScenarioContextDelegate context) {
    Location searchLocation =
        new LocationConverter().convertLocation(event.getTestCase().getLocation());
    return Streams.mapWithIndex(
            context.currentScenarioOutline().getExamples().stream()
                .map(Examples::getTableBody)
                .flatMap(List::stream)
                .map(TableRow::getLocation)
                .map(searchLocation::equals),
            Pair::of) // (locationMatches, index)
        .filter(Pair::getLeft)
        .map(Pair::getRight)
        .map(Math::toIntExact)
        .findFirst();
  }

  private Optional<Feature> featureFrom(URI currentFeaturePath) {
    return Optional.ofNullable(featureLoader.getFeature(currentFeaturePath));
  }

  private List<Step> getStepsIncludingBackgroundFromFeatureForScenario(
      Feature feature, Scenario scenario) {
    List<Step> steps = new ArrayList<>();
    feature.getChildren().stream()
        .map(FeatureChild::getBackground)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(Background::getSteps)
        .forEach(steps::addAll);
    steps.addAll(scenario.getSteps());
    return steps;
  }

  private void informWorkflowUiAboutCurrentScenario(
      Feature feature, ScenarioContextDelegate context, boolean isDryRun) {
    Scenario scenario = context.getCurrentScenarioDefinition();

    List<Step> steps = getStepsIncludingBackgroundFromFeatureForScenario(feature, scenario);

    log.info("Scenario location {}", scenario.getLocation());
    Map<String, String> variantDataMap = getVariantDataMap(context);
    log.debug(
        "Current row for scenario variant {} {}", currentScenarioDataVariantIndex, variantDataMap);
    String scenarioUniqueId =
        ScenarioRunner.findScenarioUniqueId(
                scenario,
                context.currentFeaturePath(),
                context.isAScenarioOutline(),
                currentScenarioDataVariantIndex)
            .toString();
    ScenarioUpdate scenarioUpdate =
        ScenarioUpdate.builder()
            .isDryRun(isDryRun)
            .description(replaceOutlineParameters(scenario.getName(), variantDataMap, false))
            .uniqueId(scenarioUniqueId)
            .variantIndex(currentScenarioDataVariantIndex)
            .exampleKeys(context.isAScenarioOutline() ? context.getTable().getHeaders() : null)
            .exampleList(variantDataMap)
            .steps(stepUpdates(steps, variantDataMap))
            .build();
    FeatureUpdate featureUpdate =
        FeatureUpdate.builder()
            .description(feature.getName())
            .scenarios(convertToLinkedHashMap(scenarioUniqueId, scenarioUpdate))
            .build();
    TigerDirector.getTigerTestEnvMgr()
        .receiveTestEnvUpdate(
            TigerStatusUpdate.builder()
                .featureMap(convertToLinkedHashMap(feature.getName(), featureUpdate))
                .build());
  }

  private static @NotNull <T> LinkedHashMap<String, T> convertToLinkedHashMap(String key, T value) {
    return new LinkedHashMap<>(Map.of(key, value));
  }

  private Map<String, String> getVariantDataMap(ScenarioContextDelegate context) {
    return context.isAScenarioOutline()
        ? context.getTable().row(currentScenarioDataVariantIndex).toStringMap().entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> tryResolvePlaceholders(e.getValue())))
        : Map.of();
  }

  private String replaceOutlineParameters(
      String line, Map<String, String> variantDataMap, boolean convertToHtml) {
    if (variantDataMap == null) {
      return line;
    }

    UnaryOperator<String> converter =
        convertToHtml ? StringEscapeUtils::escapeHtml4 : UnaryOperator.identity();
    String parsedLine = line;
    for (Entry<String, String> entry : variantDataMap.entrySet()) {
      String parameterReference = converter.apply("<" + entry.getKey() + ">");
      String parameterValue = converter.apply(entry.getValue());
      parsedLine = parsedLine.replace(parameterReference, parameterValue);
    }
    return parsedLine;
  }

  private String getStepToolTip(Step step) {
    return getStepDescription(step, false, false);
  }

  private String getStepDescription(Step step, boolean convertToHtml, boolean resolve) {
    UnaryOperator<String> converter =
        convertToHtml ? StringEscapeUtils::escapeHtml4 : UnaryOperator.identity();
    UnaryOperator<String> resolver =
        resolve ? SerenityReporterCallbacks::tryResolvePlaceholders : UnaryOperator.identity();
    final String resolvedText = resolver.apply(step.getText());
    final StringBuilder stepText =
        new StringBuilder(step.getKeyword()).append(converter.apply(resolvedText));
    if (convertToHtml) {
      step.getDocString()
          .map(DocString::getContent)
          .map(resolver)
          .ifPresent(
              resolvedDocStr ->
                  stepText
                      .append("<div class=\"steps-docstring\">")
                      .append(converter.apply(resolvedDocStr))
                      .append("</div>"));
      step.getDataTable()
          .ifPresent(
              dataTable -> {
                stepText.append("<br/><table class=\"table table-sm table-data-table\">");
                dataTable
                    .getRows()
                    .forEach(
                        row -> {
                          stepText.append("<tr>");
                          row.getCells().stream()
                              .map(TableCell::getValue)
                              .map(resolver)
                              .forEach(
                                  resolvedCellText ->
                                      stepText
                                          .append("<td>")
                                          .append(converter.apply(resolvedCellText))
                                          .append("</td>"));
                          stepText.append("</tr>");
                        });
                stepText.append("</table>");
              });
    } else {
      step.getDataTable()
          .ifPresent(
              dataTable -> {
                stepText.append("\n");
                dataTable
                    .getRows()
                    .forEach(
                        row -> {
                          row.getCells().stream()
                              .map(TableCell::getValue)
                              .map(resolver)
                              .forEach(
                                  resolvedCellText ->
                                      stepText
                                          .append(converter.apply(resolvedCellText))
                                          .append("\t\t\t"));
                          stepText.append("\n");
                        });
              });
    }
    return stepText.toString();
  }

  private LinkedHashMap<String, StepUpdate> stepUpdates(
      List<Step> steps, Map<String, String> outlineParameters) {
    var map = new LinkedHashMap<String, StepUpdate>();
    Streams.mapWithIndex(
            steps.stream(),
            (step, stepIndex) ->
                StepUpdate.builder()
                    .description(getDescriptionWithReplacements(step, outlineParameters))
                    .tooltip(getStepToolTip(step))
                    .status(TestResult.PENDING)
                    .stepIndex(Math.toIntExact(stepIndex))
                    .build())
        .forEach(stepUpdate -> map.put(Integer.toString(stepUpdate.getStepIndex()), stepUpdate));
    return map;
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test step start
  //
  public void handleTestStepStarted(TestStepStarted event, ScenarioContextDelegate context) {
    shouldWaitIfInPauseMode();
    shouldAbortTestExecution();

    if (!(event.getTestStep() instanceof HookTestStep)
        && event.getTestStep() instanceof PickleStepTestStep pickleTestStep) {
      boolean isDryRun = TestCaseDelegate.of(event.getTestCase()).isDryRun();
      String statusName =
          isDryRun ? TestResult.TEST_DISCOVERED.name() : TestResult.EXECUTING.name();
      informWorkflowUiAboutCurrentStep(pickleTestStep, statusName, context, isDryRun);
    }

    if (context.getCurrentStep() != null) {
      evidenceRecorder.openStepContext(
          new ReportStepConfiguration(getStepDescription(context.getCurrentStep(), true, false)));
    }
  }

  private void addBannerMessageToUpdate(
      Map<String, String> variantDataMap,
      PickleStepTestStep pickleTestStep,
      TigerStatusUpdate.TigerStatusUpdateBuilder statusUpdateBuilder) {
    Matcher m = showSteps.matcher(pickleTestStep.getStep().getText());
    if (m.find()) {
      Color col;
      String colStr = replaceOutlineParameters(m.group(2), variantDataMap, false).trim();
      try {
        if (!colStr.isEmpty()) {
          col =
              (Color)
                  Color.class
                      .getDeclaredField(RbelAnsiColors.seekColor(colStr).name().toUpperCase())
                      .get(null);
        } else {
          col = Color.BLACK;
        }
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
        col = Color.BLACK;
      }
      statusUpdateBuilder
          .bannerColor(String.format("#%06X", (0xFFFFFF & col.getRGB())))
          .bannerMessage(
              tryResolvePlaceholders(replaceOutlineParameters(m.group(4), variantDataMap, false)));
    }
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test step end
  //
  public void handleTestStepFinished(TestStepFinished event, ScenarioContextDelegate context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) return;

    if (!(event.getTestStep() instanceof HookTestStep)) {
      if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()
          && TigerDirector.isSerenityAvailable()
          && TigerDirector.getCurlLoggingFilter() != null) {
        TigerDirector.getCurlLoggingFilter().printToReport();
      }
      if (context.getCurrentStep() != null) {
        boolean isDryRun = TestCaseDelegate.of(event.getTestCase()).isDryRun();
        String statusName =
            isDryRun ? TestResult.TEST_DISCOVERED.name() : event.getResult().getStatus().name();
        informWorkflowUiAboutCurrentStep(event.getTestStep(), statusName, context, isDryRun);

        if (TigerDirector.isSerenityAvailable()) {
          addStepEvidence();
        }
      }
      currentStepIndex++;
    }
  }

  private void addStepEvidence() {
    evidenceRecorder
        .getCurrentStep()
        .ifPresent(
            step ->
                step.getEvidenceEntries()
                    .forEach(
                        entry ->
                            Serenity.recordReportData()
                                .asEvidence()
                                .withTitle(entry.getType() + " - " + entry.getTitle())
                                .andContents(new JSONObject(entry.getDetails()).toString(2))));
  }

  private void informWorkflowUiAboutCurrentStep(
      TestStep event, String status, ScenarioContextDelegate context, boolean isDryRun) {

    Scenario scenario = context.getCurrentScenarioDefinition();
    PickleStepTestStep pickleTestStep = (PickleStepTestStep) event;

    TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

    String featureName =
        featureFrom(context.currentFeaturePath()).map(Feature::getName).orElse("?");
    List<MessageMetaDataDto> stepMessagesMetaDataList =
        new ArrayList<>(LocalProxyRbelMessageListener.getInstance().getStepRbelMessages())
            .stream().map(MessageMetaDataDto::createFrom).toList();

    Map<String, String> variantDataMap = getVariantDataMap(context);

    if (!isDryRun) {
      addBannerMessageToUpdate(variantDataMap, pickleTestStep, builder);
    }

    String scenarioUniqueId =
        ScenarioRunner.findScenarioUniqueId(
                scenario,
                context.currentFeaturePath(),
                context.isAScenarioOutline(),
                currentScenarioDataVariantIndex)
            .toString();

    Step currentStep = context.getCurrentStep();
    StepUpdate currentStepUpdate =
        StepUpdate.builder()
            .description(getDescriptionWithReplacements(currentStep, variantDataMap))
            .tooltip(getStepToolTip(currentStep))
            .status(TestResult.valueOf(status))
            .stepIndex(currentStepIndex)
            .rbelMetaData(stepMessagesMetaDataList)
            .build();

    ScenarioUpdate scenarioUpdate =
        ScenarioUpdate.builder()
            .isDryRun(isDryRun)
            .description(replaceOutlineParameters(scenario.getName(), variantDataMap, false))
            .uniqueId(scenarioUniqueId)
            .variantIndex(currentScenarioDataVariantIndex)
            .exampleKeys(context.isAScenarioOutline() ? context.getTable().getHeaders() : null)
            .exampleList(variantDataMap)
            .steps(Map.of(String.valueOf(currentStepIndex), currentStepUpdate))
            .build();

    FeatureUpdate featureUpdate =
        FeatureUpdate.builder()
            .description(featureName)
            .scenarios(convertToLinkedHashMap(scenarioUniqueId, scenarioUpdate))
            .build();
    TigerDirector.getTigerTestEnvMgr()
        .receiveTestEnvUpdate(
            builder.featureMap(convertToLinkedHashMap(featureName, featureUpdate)).build());
    LocalProxyRbelMessageListener.getInstance().getStepRbelMessages().clear();
  }

  private static String tryResolvePlaceholders(String input) {
    try {
      return TigerGlobalConfiguration.resolvePlaceholders(input);
    } catch (JexlException | TigerJexlException | TigerConfigurationException e) {
      log.trace("Could not resolve placeholders in {}", input, e);
      return input;
    }
  }

  private String getDescriptionWithReplacements(Step step, Map<String, String> variantDataMap) {
    return StringUtils.abbreviate(
        replaceOutlineParameters(getStepDescription(step, true, true), variantDataMap, true),
        MAX_STEP_DESCRIPTION_DISPLAY_LENGTH.getValueOrDefault());
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test case end
  //
  public void handleTestCaseFinished(TestCaseFinished event, ScenarioContextDelegate context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
      return;
    }

    if (TestCaseDelegate.of(event.getTestCase()).isDryRun()) {
      return;
    }

    currentStepIndex = -1;
    String scenarioStatus = event.getResult().getStatus().toString();

    // dump overall status for updates while test is still running
    switch (scenarioStatus) {
      case "PASSED" -> scPassed++;
      case "ERROR", "FAILED" -> scFailed++;
      case "UNDEFINED" -> {
        /* ignore */
      }
      default -> log.warn("Unsupported scenario state: %s".formatted(scenarioStatus));
    }
    log.info(
        "------------ STATUS: {} passed {}",
        scPassed,
        scFailed > 0 ? scFailed + " failed or error" : "");

    if (TigerDirector.getLibConfig().createRbelHtmlReports) {
      createRbelLogReport(event.getTestCase().getName(), event.getTestCase().getUri());
    }

    createEvidenceFile(event, context);
    TigerGlobalConfiguration.clearLocalTestVariables();
  }

  @SneakyThrows
  private void createEvidenceFile(
      TestCaseFinished testCaseFinishedEvent, final ScenarioContextDelegate scenarioContext) {
    final EvidenceReport evidenceReport = getEvidenceReport(testCaseFinishedEvent, scenarioContext);

    if (evidenceReport.getSteps().stream().anyMatch(step -> !step.getEvidenceEntries().isEmpty())) {
      Path reportFile = createEvidenceReportFile(scenarioContext, evidenceReport);

      if (TigerDirector.isSerenityAvailable()) {
        (Serenity.recordReportData().asEvidence().withTitle("Evidence Report"))
            .downloadable()
            .fromFile(reportFile);
      }
    }
  }

  @NotNull
  private Path createEvidenceReportFile(
      ScenarioContextDelegate scenarioContext, EvidenceReport evidenceReport) throws IOException {
    var renderedReport = evidenceRenderer.render(evidenceReport);

    final Path parentDir = getEvidenceDir();

    return Files.writeString(
        parentDir.resolve(
            getFileNameFor(
                "evidence", scenarioContext.getScenarioName(), currentScenarioDataVariantIndex)),
        renderedReport,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private EvidenceReport getEvidenceReport(
      TestCaseFinished testCaseFinishedEvent, ScenarioContextDelegate scenarioContext) {
    return evidenceRecorder.getEvidenceReportForScenario(
        new ReportContext(
            scenarioContext.getScenarioName(), testCaseFinishedEvent.getTestCase().getUri()));
  }

  private void createRbelLogReport(String scenarioName, URI scenarioUri) {
    try {
      // make sure target/rbellogs folder exists
      final File folder = Paths.get(TARGET_DIR, "rbellogs").toFile();
      if (!folder.exists() && !folder.mkdirs()) {
        throw new TigerOsException("Unable to create folder '" + folder.getAbsolutePath() + "'");
      }
      var rbelRenderer = getRbelHtmlRenderer(scenarioName, scenarioUri);

      String html =
          rbelRenderer.doRender(LocalProxyRbelMessageListener.getInstance().getMessages());

      String name = getFileNameFor("rbel", scenarioName, currentScenarioDataVariantIndex);
      final File logFile = Paths.get(TARGET_DIR, "rbellogs", name).toFile();
      FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
      if (TigerDirector.isSerenityAvailable()) {
        (Serenity.recordReportData()
                .asEvidence()
                .withTitle("RBellog " + (currentScenarioDataVariantIndex + 1)))
            .downloadable()
            .fromFile(logFile.toPath());
      }
      log.info("Saved HTML report of scenario '{}' to {}", scenarioName, logFile.getAbsolutePath());
    } catch (final IOException e) {
      log.error("Unable to create/save rbel log for scenario " + scenarioName, e);
    } finally {
      LocalProxyRbelMessageListener.getInstance().clearMessages();
    }
  }

  @NotNull
  private RbelHtmlRenderer getRbelHtmlRenderer(String scenarioName, URI scenarioUri) {
    var rbelRenderer = new RbelHtmlRenderer();
    rbelRenderer.setTitle(scenarioName);
    rbelRenderer.setSubTitle(
        "<p>"
            + (currentScenarioDataVariantIndex != -1
                ? "<button class=\"js-modal-trigger\""
                    + " data-bs-target=\"modal-data-variant\">Variant "
                    + (currentScenarioDataVariantIndex + 1)
                    + "</button>"
                : "")
            + "</p><p><i>"
            + scenarioUri
            + "</i></p>");
    rbelRenderer.setVersionInfo(getTigerVersionString());
    return rbelRenderer;
  }

  public String getFileNameFor(String type, String scenarioName, int dataVariantIndex) {
    if (scenarioName.length() > 80) { // Serenity can not deal with longer filenames
      scenarioName =
          scenarioName.substring(0, 60)
              + UUID.nameUUIDFromBytes(scenarioName.getBytes(StandardCharsets.UTF_8));
    }
    if (dataVariantIndex != -1) {
      scenarioName = scenarioName + "_" + (dataVariantIndex + 1);
    }
    scenarioName =
        type
            + "_"
            + replaceSpecialCharacters(scenarioName)
            + "_"
            + sdf.format(new Date())
            + ".html";
    return scenarioName;
  }

  public String replaceSpecialCharacters(String name) {
    var result = name;
    final String[] tokenMap = {
      "ä", "ae",
      "Ä", "Ae",
      "ö", "oe",
      "Ö", "Oe",
      "ü", "ue",
      "Ü", "Ue",
      "ß", "s",
      " ", "_",
      "(", "_",
      ")", "_",
      "[", "_",
      "]", "_",
      "{", "_",
      "}", "_",
      "<", "_",
      ">", "_",
      "|", "_",
      "$", "_",
      "%", "_",
      "&", "_",
      "/", "_",
      "\\", "_",
      "?", "_",
      ":", "_",
      "*", "_",
      "\"", "_"
    };

    for (int i = 0; i < tokenMap.length; i += 2) {
      result = result.replace(tokenMap[i], tokenMap[i + 1]);
    }
    return result;
  }

  private void shouldAbortTestExecution() {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
      throw new AssertionError("Aborted test execution on user request");
    }
  }

  private void shouldWaitIfInPauseMode() {
    if (isPauseMode()) {
      log.info("Test run is paused, via Workflow Ui pause button...");
      await()
          .pollDelay(500, TimeUnit.MILLISECONDS)
          .atMost(TigerDirector.getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
          .until(
              () ->
                  !isPauseMode()
                      || TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution());
      log.info("Test run commencing...");
    }
  }
}
