/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.exceptions.TigerOsException;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import de.gematik.test.tiger.testenvmgr.env.FeatureUpdate;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.StepUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import io.cucumber.core.plugin.FeatureFileLoader;
import io.cucumber.core.plugin.ScenarioContextDelegate;
import io.cucumber.core.plugin.report.EvidenceReport.ReportContext;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.plugin.event.*;
import io.cucumber.plugin.event.Event;
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
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class SerenityReporterCallbacks {

  public static final String TARGET_DIR = "target";
  private static final Object startupMutex = new Object();
  private static RuntimeException tigerStartupFailedException;
  @Getter @Setter private static boolean pauseMode;
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
    }
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
      Event ignoredEvent, ScenarioContextDelegate context) /* NOSONAR */ {
    shouldAbortTestExecution();

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
      currentScenarioDataVariantIndex++;
    } else {
      currentScenarioDataVariantIndex = -1;
      currentScenarioID = context.getCurrentScenarioId();
    }
    currentStepIndex = 0;
    Optional<Feature> currentFeature = featureFrom(context.currentFeaturePath());
    currentFeature.ifPresent(feature -> informWorkflowUiAboutCurrentScenario(feature, context));
    evidenceRecorder.reset();
  }

  private Optional<Feature> featureFrom(URI currentFeaturePath) {
    return Optional.ofNullable(featureLoader.getFeature(currentFeaturePath));
  }

  private List<Step> getStepsIncludingBackgroundFromFeatureForScenario(
      Feature feature, Scenario scenario) {
    List<Step> steps = new ArrayList<>();
    feature.getChildren().stream()
        .filter(child -> child.getBackground().isPresent())
        .map(child -> child.getBackground().get())
        .forEach(background -> steps.addAll(background.getSteps()));
    steps.addAll(scenario.getSteps());
    return steps;
  }

  private void informWorkflowUiAboutCurrentScenario(
      Feature feature, ScenarioContextDelegate context) {
    Scenario scenario = context.getCurrentScenarioDefinition();

    List<Step> steps = getStepsIncludingBackgroundFromFeatureForScenario(feature, scenario);

    log.info("Scenario location {}", scenario.getLocation());
    Map<String, String> variantDataMap =
        context.isAScenarioOutline() ? context.getTable().currentRow().toStringMap() : null;
    log.debug(
        "Current row for scenario variant {} {}", currentScenarioDataVariantIndex, variantDataMap);
    TigerDirector.getTigerTestEnvMgr()
        .receiveTestEnvUpdate(
            TigerStatusUpdate.builder()
                .featureMap(
                    new LinkedHashMap<>(
                        Map.of(
                            feature.getName(),
                            FeatureUpdate.builder()
                                .description(feature.getName())
                                .scenarios(
                                    new LinkedHashMap<>(
                                        Map.of(
                                            mapScenarioToScenarioUpdateMap(
                                                scenario, context.isAScenarioOutline()),
                                            ScenarioUpdate.builder()
                                                .description(
                                                    replaceLineWithCurrentDataVariantValues(
                                                        scenario.getName(), variantDataMap))
                                                .variantIndex(currentScenarioDataVariantIndex)
                                                .exampleKeys(
                                                    context.isAScenarioOutline()
                                                        ? context.getTable().getHeaders()
                                                        : null)
                                                .exampleList(variantDataMap)
                                                .steps(
                                                    mapStepsToStepUpdateMap(
                                                        steps,
                                                        line ->
                                                            replaceLineWithCurrentDataVariantValues(
                                                                line, variantDataMap)))
                                                .build())))
                                .build())))
                .build());
  }

  private String mapScenarioToScenarioUpdateMap(Scenario scenario, boolean outline) {
    if (outline) {
      return (currentScenarioDataVariantIndex + "-" + scenario.getId());
    } else {
      return scenario.getId();
    }
  }

  private String replaceLineWithCurrentDataVariantValues(
      String line, Map<String, String> variantDataMap) {
    if (variantDataMap == null) {
      return line;
    }

    String parsedLine = line;
    for (Entry<String, String> entry : variantDataMap.entrySet()) {
      parsedLine = parsedLine.replace("<" + entry.getKey() + ">", "<" + entry.getValue() + ">");
    }
    return parsedLine;
  }

  private String getStepDescription(Step step) {
    final StringBuilder stepText =
        new StringBuilder(step.getKeyword()).append(StringEscapeUtils.escapeHtml4(step.getText()));
    step.getDocString()
        .ifPresent(
            docStr ->
                stepText
                    .append("<div class=\"steps-docstring\">")
                    .append(StringEscapeUtils.escapeHtml4(docStr.getContent()))
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
                        row.getCells()
                            .forEach(
                                cell ->
                                    stepText
                                        .append("<td>")
                                        .append(StringEscapeUtils.escapeHtml4(cell.getValue()))
                                        .append("</td>"));
                        stepText.append("</tr>");
                      });
              stepText.append("</table>");
            });
    return stepText.toString();
  }

  private Map<String, StepUpdate> mapStepsToStepUpdateMap(
      List<Step> steps, UnaryOperator<String> postProduction) {
    Map<String, StepUpdate> map = new LinkedHashMap<>();
    for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
      if (map.put(
              Integer.toString(stepIndex),
              StepUpdate.builder()
                  .description(postProduction.apply(getStepDescription(steps.get(stepIndex))))
                  .status(de.gematik.test.tiger.testenvmgr.env.TestResult.PENDING)
                  .stepIndex(stepIndex)
                  .build())
          != null) {
        throw new IllegalStateException("Duplicate key");
      }
    }
    return map;
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test step start
  //
  public void handleTestStepStarted(Event event, ScenarioContextDelegate context) {
    shouldWaitIfInPauseMode();
    shouldAbortTestExecution();

    TestStepStarted tssEvent = ((TestStepStarted) event);

    if (!(tssEvent.getTestStep() instanceof HookTestStep)
        && tssEvent.getTestStep() instanceof PickleStepTestStep pickleTestStep) {
      informWorkflowUiAboutCurrentStep(pickleTestStep, "EXECUTING", context);
    }

    if (context.getCurrentStep() != null) {
      evidenceRecorder.openStepContext(
          new ReportStepConfiguration(getStepDescription(context.getCurrentStep())));
    }
  }

  private void addBannerMessageToUpdate(
      Map<String, String> variantDataMap,
      PickleStepTestStep pickleTestStep,
      TigerStatusUpdate.TigerStatusUpdateBuilder statusUpdateBuilder) {
    Matcher m = showSteps.matcher(pickleTestStep.getStep().getText());
    if (m.find()) {
      Color col;
      String colStr = replaceLineWithCurrentDataVariantValues(m.group(2), variantDataMap).trim();
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
      } catch (Exception ignored) {
        col = Color.BLACK;
      }
      statusUpdateBuilder
          .bannerColor(String.format("#%06X", (0xFFFFFF & col.getRGB())))
          .bannerMessage(
              TigerGlobalConfiguration.resolvePlaceholders(
                  replaceLineWithCurrentDataVariantValues(m.group(4), variantDataMap)));
    }
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test step end
  //
  public void handleTestStepFinished(Event event, ScenarioContextDelegate context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) return;

    TestStepFinished tsfEvent = ((TestStepFinished) event);

    if (!(tsfEvent.getTestStep() instanceof HookTestStep)) {
      if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()
          && TigerDirector.isSerenityAvailable()
          && TigerDirector.getCurlLoggingFilter() != null) {
        TigerDirector.getCurlLoggingFilter().printToReport();
      }
      if (context.getCurrentStep() != null) {
        informWorkflowUiAboutCurrentStep(
            tsfEvent.getTestStep(),
            ((TestStepFinished) event).getResult().getStatus().name(),
            context);

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
      TestStep event, String status, ScenarioContextDelegate context) {

    Scenario scenario = context.getCurrentScenarioDefinition();
    PickleStepTestStep pickleTestStep = (PickleStepTestStep) event;

    TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

    String featureName =
        featureFrom(context.currentFeaturePath()).map(Feature::getName).orElse("?");
    List<MessageMetaDataDto> stepMessagesMetaDataList =
        new ArrayList<>(LocalProxyRbelMessageListener.getStepRbelMessages())
            .stream().map(MessageMetaDataDto::createFrom).toList();

    Map<String, String> variantDataMap =
        context.isAScenarioOutline() ? context.getTable().currentRow().toStringMap() : null;

    addBannerMessageToUpdate(variantDataMap, pickleTestStep, builder);

    TigerDirector.getTigerTestEnvMgr()
        .receiveTestEnvUpdate(
            builder
                .featureMap(
                    new LinkedHashMap<>(
                        Map.of(
                            featureName,
                            FeatureUpdate.builder()
                                .description(featureName)
                                .scenarios(
                                    new LinkedHashMap<>(
                                        Map.of(
                                            mapScenarioToScenarioUpdateMap(
                                                scenario, context.isAScenarioOutline()),
                                            ScenarioUpdate.builder()
                                                .description(
                                                    replaceLineWithCurrentDataVariantValues(
                                                        scenario.getName(), variantDataMap))
                                                .variantIndex(currentScenarioDataVariantIndex)
                                                .steps(
                                                    new HashMap<>(
                                                        Map.of(
                                                            String.valueOf(currentStepIndex),
                                                            StepUpdate.builder()
                                                                .description(
                                                                    replaceLineWithCurrentDataVariantValues(
                                                                        getStepDescription(
                                                                            context
                                                                                .getCurrentStep()),
                                                                        variantDataMap))
                                                                .status(
                                                                    de.gematik.test.tiger.testenvmgr
                                                                        .env.TestResult.valueOf(
                                                                        status))
                                                                .stepIndex(currentStepIndex)
                                                                .rbelMetaData(
                                                                    stepMessagesMetaDataList)
                                                                .build())))
                                                .build())))
                                .build())))
                .build());
    LocalProxyRbelMessageListener.getStepRbelMessages().clear();
  }

  // -------------------------------------------------------------------------------------------------------------------------------------
  //
  // test case end
  //
  public void handleTestCaseFinished(Event event, ScenarioContextDelegate context) {
    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) return;

    currentStepIndex = -1;
    TestCaseFinished tscEvent = ((TestCaseFinished) event);
    String scenarioStatus = tscEvent.getResult().getStatus().toString();

    // dump overall status for updates while test is still running
    switch (scenarioStatus) {
      case "PASSED" -> scPassed++;
      case "ERROR", "FAILED" -> scFailed++;
      default -> throw new UnsupportedOperationException(
          "Unsupported scenario: %s".formatted(scenarioStatus));
    }
    log.info(
        "------------ STATUS: {} passed {}",
        scPassed,
        scFailed > 0 ? scFailed + " failed or error" : "");

    if (TigerDirector.getLibConfig().createRbelHtmlReports) {
      createRbelLogReport(tscEvent.getTestCase().getName(), tscEvent.getTestCase().getUri());
    }

    createEvidenceFile((TestCaseFinished) event, context);
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

      String html = rbelRenderer.doRender(LocalProxyRbelMessageListener.getMessages());

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
    } catch (final Exception e) {
      log.error("Unable to create/save rbel log for scenario " + scenarioName, e);
    } finally {
      LocalProxyRbelMessageListener.clearMessages();
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
