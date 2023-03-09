/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

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
import java.awt.Color;
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
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class SerenityReporterCallbacks {


    public static final String TARGET_DIR = "target";
    private static RuntimeException tigerStartupFailedException;

    @Getter
    private int currentScenarioDataVariantIndex = -1;

    private final Pattern showSteps = Pattern.compile(".*TGR (zeige|show) ([\\w|üß ]*)(Banner|banner|text|Text) \"(.*)\""); // NOSONAR

    private String bulmaModalJsScript = null;

    /**
     * number of passed scenarios / scenario data variants.
     */
    @Getter
    private int scPassed = 0;
    /**
     * number of failed scenarios / scenario data variants.
     */
    @Getter
    private int scFailed = 0;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final EvidenceRecorder evidenceRecorder = EvidenceRecorderFactory.getEvidenceRecorder();
    private final EvidenceRenderer evidenceRenderer = new EvidenceRenderer(
        new HtmlEvidenceRenderer());


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
    public void handleTestRunStarted(Event ignoredEvent,
        ScenarioContextDelegate ignoredContext) {
        showTigerVersion();
        initializeTiger();
    }


    private void showTigerVersion() {
        try {
            Properties p = new Properties();
            p.load(SerenityReporterCallbacks.class.getResourceAsStream("/build.properties"));
            String version = p.getProperty("tiger.version");
            if (!version.equals("${project.version}")) {
                log.info(Ansi.colorize("Starting Tiger version " + version + "-" + p.getProperty("tiger.build.timestamp"),
                    RbelAnsiColors.GREEN_BRIGHT));
            }
        } catch (RuntimeException | IOException ignored) {
            log.info(Ansi.colorize("Starting UNKNOWN Tiger version", RbelAnsiColors.RED_BRIGHT));
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
    public void handleTestCaseStarted(Event event, ScenarioContextDelegate context) /* NOSONAR */ {

        // TGR
        if (context.isAScenarioOutline()) {
            currentScenarioDataVariantIndex++;
        }
        Optional<Feature> currentFeature = featureFrom(context.currentFeaturePath());
        currentFeature.ifPresent(feature ->
            informWorkflowUiAboutCurrentScenario(feature, context)
        );
        evidenceRecorder.reset();
    }

    FeatureFileLoader featureLoader = new FeatureFileLoader();

    private Optional<Feature> featureFrom(URI currentFeaturePath) {
        return Optional.ofNullable(featureLoader.getFeature(currentFeaturePath));
    }


    private void informWorkflowUiAboutCurrentScenario(Feature feature,
        ScenarioContextDelegate context) {
        Scenario scenario = context.getCurrentScenarioDefinition();
        log.info("Scenario location {}", scenario.getLocation());
        Map<String, String> variantDataMap = context.isAScenarioOutline() ?
            context.getTable().currentRow().toStringMap() : null;
        log.info("Current row for scenario variant {} {}", currentScenarioDataVariantIndex,
            variantDataMap);
        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(Map.of(feature.getName(),
                    FeatureUpdate.builder()
                        .description(feature.getName())
                        .scenarios(
                            new LinkedHashMap<>(Map.of(
                                mapScenarioToScenarioUpdateMap(scenario,
                                    context.isAScenarioOutline()),
                                ScenarioUpdate.builder()
                                    .description(
                                        replaceLineWithCurrentDataVariantValues(scenario.getName(),
                                            variantDataMap))
                                    .variantIndex(currentScenarioDataVariantIndex)
                                    .exampleKeys(
                                        context.isAScenarioOutline() ?
                                            context.getTable().getHeaders() : null)
                                    .exampleList(variantDataMap)
                                    .steps(mapStepsToStepUpdateMap(scenario.getSteps(), line ->
                                        replaceLineWithCurrentDataVariantValues(line, variantDataMap))
                                    ).build()
                            )))
                        .build()
                )))
            .build());
    }


    private String mapScenarioToScenarioUpdateMap(Scenario scenario, boolean outline) {
        if (outline) {
            return (currentScenarioDataVariantIndex + "-" + scenario.getId());
        } else {
            return scenario.getId();
        }
    }

    private String replaceLineWithCurrentDataVariantValues(String line, Map<String, String> variantDataMap) {
        if (variantDataMap == null) {
            return line;
        }

        String parsedLine = line;
        for (Entry<String, String> entry : variantDataMap.entrySet()) {
            parsedLine = parsedLine.replace("<" + entry.getKey() + ">",
                "<" + entry.getValue() + ">");
        }
        return parsedLine;
    }

    private String getStepDescription(Step step) {
        final StringBuilder stepText = new StringBuilder(step.getKeyword());
        stepText.append(" ").append(StringEscapeUtils.escapeHtml4(step.getText()));
        step.getDocString().ifPresent(docStr ->
            stepText.append("<div class=\"steps-docstring\">")
                .append(StringEscapeUtils.escapeHtml4(docStr.getContent()))
                .append("</div>" ));
        step.getDataTable().ifPresent(dataTable -> stepText.append("<br/>" + StringEscapeUtils.escapeHtml4(dataTable.toString())));
        return stepText.toString();
    }

    private Map<String, StepUpdate> mapStepsToStepUpdateMap(List<Step> steps, UnaryOperator<String> postProduction) {
        Map<String, StepUpdate> map = new HashMap<>();
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            if (map.put(Integer.toString(stepIndex), StepUpdate.builder()
                .description(postProduction.apply(getStepDescription(steps.get(stepIndex))))
                .status(de.gematik.test.tiger.testenvmgr.env.TestResult.PENDING)
                .stepIndex(stepIndex)
                .build()) != null) {
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
        TestStepStarted tssEvent = ((TestStepStarted) event);

        Map<String, String> variantDataMap = context.isAScenarioOutline() ?
            context.getTable().currentRow().toStringMap() : null;

        if (!(tssEvent.getTestStep() instanceof HookTestStep)
            && tssEvent.getTestStep() instanceof PickleStepTestStep) {
            PickleStepTestStep pickleTestStep = (PickleStepTestStep) tssEvent.getTestStep();

            informWorkflowUiAboutCurrentStep(pickleTestStep, "EXECUTING", context);

        }

        if (context.getCurrentStep() != null) {
            evidenceRecorder.openStepContext(
                new ReportStepConfiguration(getStepDescription(context.getCurrentStep())));
        }
    }

    private void addBannerMessageToUpdate(Map<String, String> variantDataMap, PickleStepTestStep pickleTestStep,
        TigerStatusUpdate.TigerStatusUpdateBuilder statusUpdateBuilder) {
        Matcher m = showSteps.matcher(pickleTestStep.getStep().getText());
        if (m.find()) {
            Color col;
            String colStr = replaceLineWithCurrentDataVariantValues(m.group(2), variantDataMap).trim();
            try {
                if (!colStr.isEmpty()) {
                    col = (Color) Color.class.getDeclaredField(
                        RbelAnsiColors.seekColor(colStr).name().toUpperCase()).get(null);
                } else {
                    col = Color.BLACK;
                }
            } catch (Exception ignored) {
                col = Color.BLACK;
            }
            statusUpdateBuilder
                .bannerColor(String.format("#%06X", (0xFFFFFF & col.getRGB())))
                .bannerMessage(TigerGlobalConfiguration.resolvePlaceholders(replaceLineWithCurrentDataVariantValues(m.group(4), variantDataMap)));
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------
    //
    // test step end
    //
    public void handleTestStepFinished(Event event, ScenarioContextDelegate context) {
        TestStepFinished tsfEvent = ((TestStepFinished) event);

        if (!(tsfEvent.getTestStep() instanceof HookTestStep)) {
            if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()
                && TigerDirector.isSerenityAvailable()
                && TigerDirector.curlLoggingFilter != null) {
                TigerDirector.curlLoggingFilter.printToReport();
            }
            if (context.getCurrentStep() != null) {
                informWorkflowUiAboutCurrentStep(tsfEvent.getTestStep(), ((TestStepFinished) event).getResult().getStatus().name(), context);

                if (TigerDirector.isSerenityAvailable()) {
                    addStepEvidence();
                }
            }
        }

    }

    private void addStepEvidence() {
        evidenceRecorder.getCurrentStep()
            .ifPresent(step -> step
                .getEvidenceEntries()
                .forEach(entry -> Serenity.recordReportData()
                    .asEvidence()
                    .withTitle(entry.getType() + " - " + entry.getTitle())
                    .andContents(
                        new JSONObject(
                            entry.getDetails()).toString(2))));
    }

    private void informWorkflowUiAboutCurrentStep(TestStep event, String status,
        ScenarioContextDelegate context) {

        Scenario scenario = context.getCurrentScenarioDefinition();
        PickleStepTestStep pickleTestStep = (PickleStepTestStep) event;

        int currentStepIndex = scenario.getSteps().indexOf(context.getCurrentStep());
        TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

        String featureName;
        Optional<Feature> feature = featureFrom(context.currentFeaturePath());
        if (feature.isPresent()) {
            featureName = feature.get().getName();
        } else {
            featureName = "?";
        }

        List<MessageMetaDataDto> stepMessagesMetaDataList = new ArrayList<>(LocalProxyRbelMessageListener.getStepRbelMessages()).stream()
            .map(MessageMetaDataDto::createFrom)
            .collect(Collectors.toList());

        Map<String, String> variantDataMap = context.isAScenarioOutline() ?
            context.getTable().currentRow().toStringMap() : null;

        addBannerMessageToUpdate(variantDataMap, pickleTestStep, builder);

        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(builder
            .featureMap(
                new LinkedHashMap<>(Map.of(featureName, FeatureUpdate.builder()
                    .description(featureName)
                    .scenarios(
                        new LinkedHashMap<>(Map.of(
                            mapScenarioToScenarioUpdateMap(scenario, context.isAScenarioOutline()),
                            ScenarioUpdate.builder()
                                .description(replaceLineWithCurrentDataVariantValues(scenario.getName(), variantDataMap))
                                .variantIndex(currentScenarioDataVariantIndex)
                                .steps(new HashMap<>(Map.of(String.valueOf(currentStepIndex), StepUpdate.builder()
                                    .description(
                                        replaceLineWithCurrentDataVariantValues(getStepDescription(context.getCurrentStep()), variantDataMap))
                                    .status(de.gematik.test.tiger.testenvmgr.env.TestResult.valueOf(status))
                                    .stepIndex(currentStepIndex)
                                    .rbelMetaData(stepMessagesMetaDataList)
                                    .build()
                                ))).build()
                        ))).build()
                )))
            .build());
        LocalProxyRbelMessageListener.getStepRbelMessages().clear();

    }

    // -------------------------------------------------------------------------------------------------------------------------------------
    //
    // test case end
    //
    public void handleTestCaseFinished(Event event, ScenarioContextDelegate context) {
        TestCaseFinished tscEvent = ((TestCaseFinished) event);
        String scenarioStatus = tscEvent.getResult().getStatus().toString();

        // dump overall status for updates while test is still running
        switch (scenarioStatus) {
            case "PASSED":
                scPassed++;
                break;
            case "ERROR":
            case "FAILED":
                scFailed++;
                break;
            default:
                break;
        }
        log.info("------------ STATUS: {} passed {}", scPassed,
            scFailed > 0 ? scFailed + " failed or error" : "");

        if (TigerDirector.getLibConfig().createRbelHtmlReports) {
            createRbelLogReport(tscEvent.getTestCase().getName(), tscEvent.getTestCase().getUri(),
                context);
        }

        createEvidenceFile((TestCaseFinished) event, context);
    }

    @SneakyThrows
    private void createEvidenceFile(
        TestCaseFinished testCaseFinishedEvent, final ScenarioContextDelegate scenarioContext) {
        final EvidenceReport evidenceReport = getEvidenceReport(testCaseFinishedEvent,
            scenarioContext);

        if (evidenceReport.getSteps().stream().anyMatch(step -> !step.getEvidenceEntries().isEmpty())) {
            Path reportFile = createReportFile(scenarioContext, evidenceReport);

            if (TigerDirector.isSerenityAvailable()) {
                (Serenity.recordReportData().asEvidence()
                    .withTitle("Evidence Report"))
                    .downloadable()
                    .fromFile(reportFile);
            }
        }
    }

    @NotNull
    private Path createReportFile(ScenarioContextDelegate scenarioContext,
        EvidenceReport evidenceReport)
        throws IOException {
        var renderedReport = evidenceRenderer.render(evidenceReport);

        final Path parentDir = getEvidenceDir();

        return Files.write(
            parentDir.resolve(
                getFileNameFor("evidence", scenarioContext.getScenarioName(), currentScenarioDataVariantIndex)),
            renderedReport.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    @NotNull
    private static Path getEvidenceDir() throws IOException {
        final Path parentDir = Path.of(TARGET_DIR,
            "evidences");
        if (Files.notExists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        return parentDir;
    }

    private EvidenceReport getEvidenceReport(TestCaseFinished testCaseFinishedEvent,
        ScenarioContextDelegate scenarioContext) {
        return evidenceRecorder.getEvidenceReportForScenario(
            new ReportContext(scenarioContext.getScenarioName(),
                testCaseFinishedEvent.getTestCase().getUri()));
    }

    private void createRbelLogReport(String scenarioName, URI scenarioUri,
        ScenarioContextDelegate context) {
        try {
            // make sure target/rbellogs folder exists
            final File folder = Paths.get(TARGET_DIR, "rbellogs").toFile();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new TigerOsException(
                    "Unable to create folder '" + folder.getAbsolutePath() + "'");
            }
            var rbelRenderer = new RbelHtmlRenderer();
            rbelRenderer.setSubTitle(
                "<p><b>" + scenarioName + "</b>&nbsp&nbsp;"
                    + (currentScenarioDataVariantIndex != -1 ?
                    "<button class=\"js-modal-trigger\" data-target=\"modal-data-variant\">Variant " + (
                        currentScenarioDataVariantIndex + 1) + "</button>" :
                    "")
                    + "</p><p><i>" + scenarioUri + "</i></p>");
            String html = rbelRenderer.doRender(LocalProxyRbelMessageListener.getMessages());

            if (currentScenarioDataVariantIndex != -1) {
                StringBuilder modal = new StringBuilder("<div id=\"modal-data-variant\" class=\"modal\">\n"
                    + "  <div class=\"modal-background\"></div>\n"
                    + "  <div class=\"modal-content\">\n"
                    + "    <div class=\"box\"><h2>Scenario Data</h2><table class=\"table is-striped is-hoverable is-fullwidth\">\n");
                for (Entry<String, String> entry : context.getTable().currentRow().toStringMap().entrySet()) {
                    modal.append("<tr><th>").append(entry.getKey()).append("</th><td>").append(entry.getValue())
                        .append("</td></tr>");
                }
                modal.append("    </table></div>\n</div>\n</div>\n");

                loadBulma();
            }
            String name = getFileNameFor("rbel", scenarioName, currentScenarioDataVariantIndex);
            final File logFile = Paths.get(TARGET_DIR, "rbellogs", name).toFile();
            FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
            if (TigerDirector.isSerenityAvailable()) {
                (Serenity.recordReportData().asEvidence()
                    .withTitle("RBellog " + (currentScenarioDataVariantIndex + 1))).downloadable()
                    .fromFile(logFile.toPath());
            }
            log.info("Saved HTML report of scenario '{}' to {}", scenarioName,
                logFile.getAbsolutePath());
        } catch (final Exception e) {
            log.error("Unable to create/save rbel log for scenario " + scenarioName, e);
        }
    }

    private void loadBulma() throws IOException {
        if (bulmaModalJsScript == null) {
            try {
                bulmaModalJsScript = IOUtils.toString(
                    getClass().getResourceAsStream("/js/bulma-modal.js"),
                    StandardCharsets.UTF_8);
            } catch (NullPointerException npe) {
                log.error("Unable to locate bulma-modal.js in class path!");
            }
        }
    }

    public String getFileNameFor(String type, String scenarioName, int dataVariantIndex) {
        if (scenarioName.length() > 80) { // Serenity can not deal with longer filenames
            scenarioName = scenarioName.substring(0, 60) + UUID.nameUUIDFromBytes(
                scenarioName.getBytes(StandardCharsets.UTF_8));
        }
        if (dataVariantIndex != -1) {
            scenarioName = scenarioName + "_" + (dataVariantIndex + 1);
        }
        scenarioName =
            type + "_" + replaceSpecialCharacters(scenarioName) + "_" + sdf.format(new Date()) + ".html";
        return scenarioName;
    }

    public String replaceSpecialCharacters(String name) {
        final String tokenMap = "äaÄAöoÖOüuÜUßs _(_)_[_]_{_}_<_>_|_$_%_&_/_\\_?_:_*_\"_";
        for (int i = 0; i < tokenMap.length(); i += 2) {
            name = name.replace(tokenMap.charAt(i), tokenMap.charAt(i + 1));
        }
        return name;
    }

}
