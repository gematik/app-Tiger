/*
 *
 * Copyright 2021-2025 gematik GmbH
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

import static org.awaitility.Awaitility.await;

import com.google.common.collect.Streams;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.exceptions.TigerOsException;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerInitializer;
import de.gematik.test.tiger.lib.rbel.RbelMessageRetriever;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.env.FeatureUpdate;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.StepUpdate;
import de.gematik.test.tiger.testenvmgr.env.TestResult;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.core.plugin.FeatureFileLoader;
import io.cucumber.core.plugin.IScenarioContext;
import io.cucumber.core.plugin.SerenityUtils;
import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.core.runner.TestCaseDelegate;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Location;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.plugin.event.Event;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
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
import lombok.val;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.core.listeners.AbstractStepListener;
import net.thucydides.model.domain.TestOutcome;
import net.thucydides.model.screenshots.ScreenshotAndHtmlSource;
import net.thucydides.model.steps.ExecutedStepDescription;
import net.thucydides.model.steps.StepFailure;
import org.apache.commons.io.FileUtils;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Slf4j
public class SerenityReporterCallbacks extends AbstractStepListener {

  public static final String TARGET_DIR = "target";
  public final TigerInitializer tigerInitializer = new TigerInitializer();
  @Getter @Setter private static boolean pauseMode;
  private final ThreadLocal<Boolean> scenarioAlreadyFailed =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  @SuppressWarnings("java:S5852")
  private final Pattern showSteps =
      Pattern.compile(
          ".*TGR (zeige|show) ([\\w|üß ]*)(Banner|banner|text|Text) \"(.*)\""); // NOSONAR

  private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
  private final EvidenceRecorder evidenceRecorder = EvidenceRecorderFactory.getEvidenceRecorder();
  private final EvidenceRenderer evidenceRenderer =
      new EvidenceRenderer(new HtmlEvidenceRenderer());
  FeatureFileLoader featureLoader = new FeatureFileLoader();

  /** number of passed scenarios / scenario data variants. */
  @Getter private int scPassed = 0;

  /** number of failed scenarios / scenario data variants. */
  @Getter private int scFailed = 0;

  private final FeatureExecutionMonitor featureExecutionMonitor = new FeatureExecutionMonitor();

  private static final ThreadLocal<TigerStatusUpdate> currentStatusUpdate = new ThreadLocal<>();
  private static final ThreadLocal<Stack<StepUpdate>> currentSteps = new ThreadLocal<>();

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
  public void handleTestRunStarted(Event ignoredEvent) {
    tigerInitializer.runWithSafelyInitialized(
        () -> {
          shouldAbortTestExecution();
          featureExecutionMonitor.startTestRun();
        });
  }

  @SuppressWarnings("java:S1172")
  public void handleTestRunFinished(TestRunFinished ignoredEvent) {
    scenarioAlreadyFailed.remove();
    featureExecutionMonitor.stopTestRun();
  }

  private String getTigerVersionString() {
    return tigerInitializer.getTigerVersionString();
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test case start
  //
  public void handleTestCaseStarted(
      TestCaseStarted testCaseStartedEvent, IScenarioContext context) {
    shouldAbortTestExecution();
    scenarioAlreadyFailed.set(Boolean.FALSE);

    Optional<Feature> currentFeature = featureFrom(context.getFeatureURI());

    var testCase = testCaseStartedEvent.getTestCase();
    boolean isDryRun = TestCaseDelegate.of(testCase).isDryRun();
    currentFeature.ifPresent(
        feature -> informWorkflowUiAboutCurrentScenario(feature, testCase, context, isDryRun));

    evidenceRecorder.reset();
    featureExecutionMonitor.startTestCase(testCaseStartedEvent);
  }

  public int extractScenarioDataVariantIndex(IScenarioContext context, TestCase testCase) {
    Location searchLocation = new LocationConverter().convertLocation(testCase.getLocation());
    var scenarioId = scenarioIdFrom(testCase);
    return Streams.mapWithIndex(
            context.currentScenarioOutline(scenarioId).getExamples().stream()
                .map(Examples::getTableBody)
                .flatMap(List::stream)
                .map(TableRow::getLocation)
                .map(searchLocation::equals),
            Pair::of) // (locationMatches, index)
        .filter(Pair::getLeft)
        .map(Pair::getRight)
        .map(Math::toIntExact)
        .findFirst()
        .orElse(-1);
  }

  private Optional<Feature> featureFrom(URI currentFeaturePath) {
    return Optional.ofNullable(featureLoader.getFeature(currentFeaturePath));
  }

  private void informWorkflowUiAboutCurrentScenario(
      Feature feature, TestCase testCase, IScenarioContext context, boolean isDryRun) {
    String scenarioId = scenarioIdFrom(testCase);
    Scenario scenario = context.getCurrentScenarioDefinition(scenarioId);

    int dataVariantIndex = extractScenarioDataVariantIndex(context, testCase);

    log.info("Scenario location {}", scenario.getLocation());

    Map<String, String> variantDataMap = getVariantDataMap(context, scenarioId, dataVariantIndex);
    log.debug("Current row for scenario variant {} {}", dataVariantIndex, variantDataMap);
    String scenarioUniqueId =
        ScenarioRunner.findScenarioUniqueId(
                context.getFeatureURI(), convertLocation(testCase.getLocation()))
            .toString();
    ScenarioUpdate scenarioUpdate =
        ScenarioUpdate.builder()
            .isDryRun(isDryRun)
            .description(replaceOutlineParameters(scenario.getName(), variantDataMap, false))
            .uniqueId(scenarioUniqueId)
            .variantIndex(dataVariantIndex)
            .exampleKeys(
                context.isAScenarioOutline(scenarioId)
                    ? context.getTable(scenarioId).getHeaders()
                    : null)
            .exampleList(variantDataMap)
            .steps(stepUpdates(StepDescription.extractStepDescriptions(testCase)))
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

  private Map<String, String> getVariantDataMap(
      IScenarioContext context, String scenarioId, int dataVariantIndex) {
    if (context.isAScenarioOutline(scenarioId)) {
      List<Examples> examples = context.currentScenarioOutline(scenarioId).getExamples();
      var headers =
          examples
              .get(0)
              .getTableHeader()
              .map(SerenityReporterCallbacks::getCellValues)
              .orElse(Collections.emptyList());
      var values =
          findExampleRow(dataVariantIndex, examples)
              .map(SerenityReporterCallbacks::getCellValues)
              .orElse(Collections.emptyList());
      return createMap(headers, values);
    } else {
      return Map.of();
    }
  }

  private static @NotNull List<String> getCellValues(TableRow row) {
    return row.getCells().stream().map(TableCell::getValue).toList();
  }

  private static @NotNull <A, B> Map<A, B> createMap(List<A> keys, List<B> values) {
    HashMap<A, B> map = new HashMap<>();
    var it1 = keys.iterator();
    var it2 = values.iterator();
    while (it1.hasNext() && it2.hasNext()) {
      map.put(it1.next(), it2.next());
    }
    return map;
  }

  private static @NotNull Optional<TableRow> findExampleRow(
      int dataVariantIndex, List<Examples> examples) {
    return examples.stream()
        .map(Examples::getTableBody)
        .flatMap(List::stream)
        .skip(dataVariantIndex)
        .findFirst();
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

  private LinkedHashMap<String, StepUpdate> stepUpdates(List<StepDescription> testSteps) {
    var map = new LinkedHashMap<String, StepUpdate>();
    Streams.mapWithIndex(
            testSteps.stream(),
            (step, stepIndex) ->
                StepUpdate.builder()
                    .description(step.getUnresolvedDescriptionHtml())
                    .tooltip(step.getTooltip())
                    .status(TestResult.PENDING)
                    .stepIndex(Math.toIntExact(stepIndex))
                    .build())
        .forEach(stepUpdate -> map.put(Integer.toString(stepUpdate.getStepIndex()), stepUpdate));
    return map;
  }

  public enum StepState {
    STARTED,
    FINISHED
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test step start
  //
  public void handleTestStepStarted(TestStepStarted event, IScenarioContext context) {
    shouldWaitIfInPauseMode();
    shouldAbortTestExecution();

    var testCase = event.getTestCase();
    TestStep testStep = event.getTestStep();
    if (!(testStep instanceof HookTestStep) && testStep instanceof PickleStepTestStep) {
      var result =
          Boolean.TRUE.equals(scenarioAlreadyFailed.get())
              ? TestResult.SKIPPED
              : TestResult.EXECUTING;
      updateStepInformation(context, testCase, testStep, result, null, StepState.STARTED);
      evidenceRecorder.openStepContext(
          new ReportStepConfiguration(
              StepDescription.of((PickleStepTestStep) testStep).getUnresolvedDescriptionHtml()));
    }
  }

  private void updateStepInformation(
      IScenarioContext context,
      TestCase testCase,
      TestStep testStep,
      TestResult result,
      Throwable error,
      StepState stepState) {
    var dryRun = TestCaseDelegate.of(testCase).isDryRun();
    var status = dryRun ? TestResult.TEST_DISCOVERED : result;
    int dataVariantIndex = extractScenarioDataVariantIndex(context, testCase);
    informWorkflowUiAboutCurrentStep(
        context, testCase, testStep, status, error, dryRun, dataVariantIndex, stepState);
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
  public void handleTestStepFinished(TestStepFinished event, IScenarioContext context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) return;

    TestStep testStep = event.getTestStep();
    if (!(testStep instanceof HookTestStep)) {
      if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()
          && TigerDirector.isSerenityAvailable()
          && TigerDirector.getCurlLoggingFilter() != null) {
        TigerDirector.getCurlLoggingFilter().printToReport();
      }

      resetSubSteps();

      var testCase = event.getTestCase();
      if (context.getCurrentStep(testCase) != null) {
        var result = TestResult.from(event.getResult().getStatus());
        if (TestResult.FAILED.equals(result)) {
          scenarioAlreadyFailed.set(true);
        }
        var error = event.getResult().getError();
        updateStepInformation(context, testCase, testStep, result, error, StepState.FINISHED);

        if (TigerDirector.isSerenityAvailable()) {
          addStepEvidence();
        }
      }
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
      IScenarioContext context,
      TestCase testCase,
      TestStep testStep,
      TestResult status,
      Throwable error,
      boolean isDryRun,
      int variantDataIndex,
      StepState stepState) {

    String scenarioId = scenarioIdFrom(testCase);
    Scenario scenario = context.getCurrentScenarioDefinition(scenarioId);
    PickleStepTestStep pickleTestStep = (PickleStepTestStep) testStep;

    Optional<Feature> feature = featureFrom(context.getFeatureURI());
    var steps = testCase.getTestSteps();
    var stepIndex = findStepIndex(pickleTestStep, steps);

    TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

    String featureName = feature.map(Feature::getName).orElse("?");

    Map<String, String> variantDataMap = getVariantDataMap(context, scenarioId, variantDataIndex);

    if (!isDryRun) {
      addBannerMessageToUpdate(variantDataMap, pickleTestStep, builder);
    }

    String scenarioUniqueId =
        ScenarioRunner.findScenarioUniqueId(
                context.getFeatureURI(), convertLocation(testCase.getLocation()))
            .toString();

    val stepDescription = StepDescription.of(pickleTestStep);
    val currentStepMessages = getCurrentStepMessages(isDryRun, stepState);
    val messageMetaData = getMessageMetaData(currentStepMessages);

    StepUpdate currentStepUpdate =
        StepUpdate.builder()
            .description(getHtmlDescription(stepDescription, isDryRun, status))
            .tooltip(stepDescription.getTooltip())
            .status(status)
            .failureMessage(error != null ? error.getMessage() : null)
            .failureStacktrace(getStackTrace(error))
            .stepIndex(stepIndex)
            .rbelMetaData(messageMetaData)
            .build();

    val scenarioUpdate =
        ScenarioUpdate.builder()
            .isDryRun(isDryRun)
            .description(replaceOutlineParameters(scenario.getName(), variantDataMap, false))
            .uniqueId(scenarioUniqueId)
            .variantIndex(variantDataIndex)
            .exampleKeys(
                context.isAScenarioOutline(scenarioId)
                    ? context.getTable(scenarioId).getHeaders()
                    : null)
            .exampleList(variantDataMap)
            .steps(Map.of(String.valueOf(stepIndex), currentStepUpdate))
            .failureMessage(error != null ? error.getMessage() : null)
            .build();

    if (TestResult.EXECUTING.equals(status)) {
      stepDescription.recordMultilineDocstringArgument();
      stepDescription.recordResolvedDescription();
    }
    val featureUpdate =
        FeatureUpdate.builder()
            .description(featureName)
            .scenarios(convertToLinkedHashMap(scenarioUniqueId, scenarioUpdate))
            .build();
    TigerStatusUpdate tigerStatusUpdate =
        builder.featureMap(convertToLinkedHashMap(featureName, featureUpdate)).build();
    if (!isDryRun && stepState == StepState.STARTED) {
      resetStepListener(context);
      currentSteps.get().push(currentStepUpdate);
      currentStatusUpdate.set(tigerStatusUpdate);
    }
    TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(tigerStatusUpdate);
    LocalProxyRbelMessageListener.getInstance().removeStepRbelMessages(currentStepMessages);
  }

  private static @Nullable String getStackTrace(Throwable error) {
    String stackTrace = null;
    if (error != null) {
      StringWriter sw = new StringWriter();
      error.printStackTrace(new PrintWriter(sw));
      stackTrace = sw.toString();
      log.info("Stack trace for error: \n{}", stackTrace);
    }
    return stackTrace;
  }

  private void updateLastStepMessages(
      IScenarioContext context, TestCase testCase, int variantDataIndex) {

    val steps = testCase.getTestSteps();

    if (steps.isEmpty()) {
      return;
    }

    if (steps.get(steps.size() - 1) instanceof PickleStepTestStep pickleTestStep) {

      val feature = featureFrom(context.getFeatureURI());

      val stepIndex = findStepIndex(pickleTestStep, steps);

      val featureName = feature.map(Feature::getName).orElse("?");

      val scenarioUniqueId =
          ScenarioRunner.findScenarioUniqueId(
                  context.getFeatureURI(), convertLocation(testCase.getLocation()))
              .toString();

      val currentStepMessages = getCurrentStepMessages(false, StepState.FINISHED);

      if (currentStepMessages.isEmpty()) {
        return;
      }

      val messageMetaData = getMessageMetaData(currentStepMessages);

      val currentStepUpdate =
          StepUpdate.builder()
              .status(TestResult.UNUSED)
              .stepIndex(stepIndex)
              .rbelMetaData(messageMetaData)
              .build();

      val scenarioUpdate =
          ScenarioUpdate.builder()
              .uniqueId(scenarioUniqueId)
              .status(TestResult.UNUSED)
              .steps(Map.of(String.valueOf(stepIndex), currentStepUpdate))
              .variantIndex(variantDataIndex)
              .build();

      val featureUpdate =
          FeatureUpdate.builder()
              .description(featureName)
              .scenarios(convertToLinkedHashMap(scenarioUniqueId, scenarioUpdate))
              .build();

      val statusUpdate =
          TigerStatusUpdate.builder()
              .featureMap(convertToLinkedHashMap(featureName, featureUpdate))
              .build();

      TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(statusUpdate);

      LocalProxyRbelMessageListener.getInstance().removeStepRbelMessages(currentStepMessages);
    }
  }

  private String getHtmlDescription(
      StepDescription description, boolean isDryRun, TestResult stepResult) {
    if (isDryRun) {
      return description.getUnresolvedDescriptionHtml();
    } else if (TestResult.EXECUTING.equals(stepResult)) {
      return description.getResolvedDescriptionHtml();
    } else {
      // Sending an empty string so that when the test is not Executing, the description is not
      // changed.
      return "";
    }
  }

  private static @NotNull List<MessageMetaDataDto> getMessageMetaData(
      List<RbelElement> currentStepMessages) {
    return currentStepMessages.stream()
        .map(MessageMetaDataDto::createFrom)
        // to allow later modification in model when step is actually performed
        .collect(Collectors.toCollection(ArrayList::new));
  }

  public static List<RbelElement> getCurrentStepMessages(boolean isDryRun, StepState stepState) {
    if (isDryRun || stepState != StepState.FINISHED) {
      return Collections.emptyList();
    }
    val waitTime = RbelMessageRetriever.RBEL_REQUEST_TIMEOUT.getValueOrDefault();
    try {
      // TODO max wait mit timeout UND nur zurück geben auf was wir hier schon gewartet
      // val myStepMessages = tigerProxy.currentMessages();
      // tigerProxy.waitFor(myStepMessages);
      // return myStepMessages;
      Awaitility.await()
          .atMost(waitTime, TimeUnit.SECONDS)
          .pollInterval(200, TimeUnit.MILLISECONDS)
          .until(
              SerenityReporterCallbacks::getFullyProcessedStepMessages,
              SerenityReporterCallbacks::allRequestsPaired);
    } catch (ConditionTimeoutException e) {
      log.atWarn()
          .addArgument(waitTime)
          .log("Not all messages are processed and paired after {} seconds.");
    }
    return LocalProxyRbelMessageListener.getInstance().getStepRbelMessages();
  }

  private static List<RbelElement> getFullyProcessedStepMessages() {
    TigerDirector.getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .ifPresent(TigerProxy::waitForAllCurrentMessagesToBeParsed);
    return LocalProxyRbelMessageListener.getInstance().getStepRbelMessages();
  }

  private static boolean allRequestsPaired(List<RbelElement> stepMessages) {
    var messages = new HashSet<>(stepMessages);

    var requestsWaitingForResponses =
        stepMessages.stream()
            .filter(message -> message.hasFacet(RbelRequestFacet.class))
            .filter(
                message ->
                    message
                        .getFacet(TracingMessagePairFacet.class)
                        .map(TracingMessagePairFacet::getResponse)
                        .stream()
                        .noneMatch(messages::contains))
            .toList();
    if (!requestsWaitingForResponses.isEmpty()) {
      log.atDebug()
          .addArgument(
              () -> requestsWaitingForResponses.stream().map(RbelElement::getUuid).toList())
          .log("Non-paired requests: {}");
    }
    return requestsWaitingForResponses.isEmpty();
  }

  private static int findStepIndex(TestStep step, List<TestStep> steps) {
    var pickleSteps = steps.stream().filter(PickleStepTestStep.class::isInstance).toList();
    return pickleSteps.indexOf(step);
  }

  private static Location convertLocation(io.cucumber.plugin.event.Location location) {
    return new LocationConverter().convertLocation(location);
  }

  private static String tryResolvePlaceholders(String input) {
    try {
      return TigerGlobalConfiguration.resolvePlaceholders(input);
    } catch (JexlException | TigerJexlException | TigerConfigurationException e) {
      log.trace("Could not resolve placeholders in {}", input, e);
      return input;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test case end
  //
  public void handleTestCaseFinished(TestCaseFinished event, IScenarioContext context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
      return;
    }

    TestCase testCase = event.getTestCase();
    if (TestCaseDelegate.of(testCase).isDryRun()) {
      return;
    }

    int dataVariantIndex = extractScenarioDataVariantIndex(context, testCase);

    updateLastStepMessages(context, testCase, dataVariantIndex);

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
      createRbelLogReport(testCase.getName(), testCase.getUri(), dataVariantIndex);
    }

    createEvidenceFile(event, context, scenarioIdFrom(testCase));
    TigerGlobalConfiguration.clearLocalTestVariables();
  }

  @SneakyThrows
  private void createEvidenceFile(
      TestCaseFinished testCaseFinishedEvent,
      final IScenarioContext scenarioContext,
      String scenarioId) {
    final EvidenceReport evidenceReport =
        getEvidenceReport(testCaseFinishedEvent, scenarioContext, scenarioId);

    if (evidenceReport.getSteps().stream().anyMatch(step -> !step.getEvidenceEntries().isEmpty())) {
      int dataVariantIndex =
          extractScenarioDataVariantIndex(scenarioContext, testCaseFinishedEvent.getTestCase());
      Path reportFile =
          createEvidenceReportFile(scenarioContext, evidenceReport, scenarioId, dataVariantIndex);

      if (TigerDirector.isSerenityAvailable()) {
        (Serenity.recordReportData().asEvidence().withTitle("Evidence Report"))
            .downloadable()
            .fromFile(reportFile);
      }
    }
  }

  @NotNull
  private Path createEvidenceReportFile(
      IScenarioContext scenarioContext,
      EvidenceReport evidenceReport,
      String scenarioId,
      int variantDataIndex)
      throws IOException {
    var renderedReport = evidenceRenderer.render(evidenceReport);

    final Path parentDir = getEvidenceDir();

    return Files.writeString(
        parentDir.resolve(
            getFileNameFor(
                "evidence",
                scenarioContext.getCurrentScenarioDefinition(scenarioId).getName(),
                variantDataIndex)),
        renderedReport,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private EvidenceReport getEvidenceReport(
      TestCaseFinished testCaseFinishedEvent, IScenarioContext scenarioContext, String scenarioId) {
    return evidenceRecorder.getEvidenceReportForScenario(
        new ReportContext(
            scenarioContext.getCurrentScenarioDefinition(scenarioId).getName(),
            testCaseFinishedEvent.getTestCase().getUri()));
  }

  private void createRbelLogReport(String scenarioName, URI scenarioUri, int variantDataIndex) {
    try {
      // make sure target/rbellogs folder exists
      final File folder = Paths.get(TARGET_DIR, "rbellogs").toFile();
      if (!folder.exists() && !folder.mkdirs()) {
        throw new TigerOsException("Unable to create folder '" + folder.getAbsolutePath() + "'");
      }
      var rbelRenderer = getRbelHtmlRenderer(scenarioName, scenarioUri, variantDataIndex);

      String html =
          rbelRenderer.doRender(LocalProxyRbelMessageListener.getInstance().getMessages());

      String name = getFileNameFor("rbel", scenarioName, variantDataIndex);
      final File logFile = Paths.get(TARGET_DIR, "rbellogs", name).toFile();
      FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
      if (TigerDirector.isSerenityAvailable()) {
        (Serenity.recordReportData().asEvidence().withTitle("RBellog " + (variantDataIndex + 1)))
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
  private RbelHtmlRenderer getRbelHtmlRenderer(
      String scenarioName, URI scenarioUri, int dataVariantIndex) {
    var rbelRenderer = new RbelHtmlRenderer();
    rbelRenderer.setTitle(scenarioName);
    rbelRenderer.setSubTitle(
        "<p>"
            + (dataVariantIndex != -1
                ? "<button class=\"js-modal-trigger\""
                    + " data-bs-target=\"modal-data-variant\">Variant "
                    + (dataVariantIndex + 1)
                    + "</button>"
                : "")
            + "</p><p><i>"
            + scenarioUri
            + "</i></p>");
    rbelRenderer.setVersionInfo(getTigerVersionString());
    return rbelRenderer;
  }

  public String getFileNameFor(String type, String scenarioName, int dataVariantIndex) {
    scenarioName = replaceSpecialCharacters(scenarioName);
    if (scenarioName.length() > 30) { // Windows files-system can not deal with longer filenames
      scenarioName =
          scenarioName.substring(0, 30)
              + UUID.nameUUIDFromBytes(scenarioName.getBytes(StandardCharsets.UTF_8));
    }
    if (dataVariantIndex != -1) {
      scenarioName += "_" + (dataVariantIndex + 1);
    }
    return type + "_" + scenarioName + "_" + sdf.format(new Date()) + ".html";
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

  public String scenarioIdFrom(TestCase testCase) {
    return SerenityUtils.scenarioIdFrom(featureLoader, testCase);
  }

  private void resetSubSteps() {
    currentSteps.remove();
    currentStatusUpdate.remove();
  }

  private void resetStepListener(IScenarioContext context) {
    resetSubSteps();
    currentSteps.set(new Stack<>());
    context.stepEventBus().registerListener(this);
  }

  private void beginStep(ExecutedStepDescription description) {
    Stack<StepUpdate> stepUpdates = currentSteps.get();
    if (stepUpdates != null && !stepUpdates.isEmpty()) {
      List<StepUpdate> currentSubSteps = stepUpdates.peek().getSubSteps();
      StepUpdate subStep =
          StepUpdate.builder()
              .description(description.getTitle())
              .stepIndex(currentSubSteps.size())
              .status(TestResult.EXECUTING)
              .build();
      currentSubSteps.add(subStep);
      stepUpdates.push(subStep);

      updateStatus();
    }
  }

  private void endStep() {
    Stack<StepUpdate> stepUpdates = currentSteps.get();
    if (stepUpdates != null && !stepUpdates.isEmpty()) {
      var step = stepUpdates.pop();
      if (!stepUpdates.isEmpty() && step.getStatus() != TestResult.FAILED) {
        step.setStatus(TestResult.PASSED);
        updateStatus();
      }
    }
  }

  private void handleStepFailure(StepFailure stepFailure) {
    Stack<StepUpdate> stepUpdates = currentSteps.get();
    if (stepUpdates != null && stepUpdates.size() > 1) {
      StepUpdate currentStep = stepUpdates.peek();
      currentStep.setStatus(TestResult.FAILED);
      currentStep.setFailureMessage(stepFailure.getMessage());
      currentStep.setFailureStacktrace(getStackTrace(stepFailure.getException()));
      updateStatus();
    }
  }

  private void updateStatus() {
    TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(currentStatusUpdate.get());
  }

  @Override
  public void stepStarted(ExecutedStepDescription description) {
    beginStep(description);
  }

  @Override
  public void stepStarted(ExecutedStepDescription description, ZonedDateTime startTime) {
    beginStep(description);
  }

  @Override
  public void stepFinished(List<ScreenshotAndHtmlSource> screenshotList) {
    endStep();
  }

  @Override
  public void stepFinished(List<ScreenshotAndHtmlSource> list, ZonedDateTime zonedDateTime) {
    endStep();
  }

  @Override
  public void stepFinished() {
    endStep();
  }

  @Override
  public void stepFailed(
      StepFailure stepFailure,
      List<ScreenshotAndHtmlSource> list,
      boolean b,
      ZonedDateTime zonedDateTime) {
    handleStepFailure(stepFailure);
  }

  @Override
  public void stepFailed(
      StepFailure failure,
      List<ScreenshotAndHtmlSource> screenshotList,
      boolean isInDataDrivenTest) {
    handleStepFailure(failure);
  }

  @Override
  public void testStarted(String s, String s1) {
    // no-op
  }

  @Override
  public void testStarted(String s, String s1, ZonedDateTime zonedDateTime) {
    // no-op
  }

  @Override
  public void testFinished(TestOutcome testOutcome, boolean b, ZonedDateTime zonedDateTime) {
    resetSubSteps();
  }

  @Override
  public void testRunFinished() {
    // no-op
  }

  @Override
  public void takeScreenshots(List<ScreenshotAndHtmlSource> list) {
    // no-op
  }

  @Override
  public void takeScreenshots(
      net.thucydides.model.domain.TestResult testResult, List<ScreenshotAndHtmlSource> list) {
    // no-op
  }
}
