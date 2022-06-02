/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.exceptions.TigerOsException;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.Scenario;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.proxy.data.MessageMetaDataDto;
import de.gematik.test.tiger.testenvmgr.env.*;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.Plugin;
import io.cucumber.plugin.event.*;
import java.awt.Color;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Getter
public class TigerCucumberListener implements ConcurrentEventListener, Plugin {

    private static String bulmaModalJsScript = null;


    /**
     * map of features parsed, based on their uri
     */
    private final Map<URI, Feature> uriFeatureMap = new HashMap<>();

    /**
     * map of features parsed, for each scenario based on its id.
     */
    private final Map<String, Feature> idFeatureMap = new HashMap<>();

    /**
     * map of steps for each scenario, identified by the scenario id.
     */
    private final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();

    private String currentScenarioId;

    /**
     * index of currently executed step.
     */
    private int currentStepIndex;

    /**
     * For scenario outlines, this index is used to identify the current data variant under execution.
     */
    private int currentScenarioDataVariantIndex = -1;
    /**
     * For scenario outlines, this list is the list of keys in the example section.
     */
    private List<String> currentScenarioDataVariantKeys = null;
    /**
     * For scenario outlines, this list is the list of data variant maps. The map contains the value for the specific variant identified by
     * its key.
     */
    private List<Map<String, String>> currentScenarioDataVariant = null;

    /**
     * number of passed scenarios / scenario data variants.
     */
    private int scPassed = 0;
    /**
     * number of failed scenarios / scenario data variants.
     */
    private int scFailed = 0;

    private final Pattern showSteps = Pattern.compile(".*TGR (zeige|show) ([\\w|üß ]*)(Banner|banner|text|Text) \"(.*)\"");

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestSourceRead.class, sourceRead);
        eventPublisher.registerHandlerFor(TestCaseStarted.class, caseStarted);
        eventPublisher.registerHandlerFor(TestStepStarted.class, stepStarted);
        eventPublisher.registerHandlerFor(TestStepFinished.class, stepFinished);
        eventPublisher.registerHandlerFor(TestCaseFinished.class, caseFinished);
    }

    private final EventHandler<TestSourceRead> sourceRead = event -> {
        log.debug("Parsing feature file {}", event.getUri());
        uriFeatureMap.put(event.getUri(), new FeatureParser().parseFeatureFile(event.getUri()));
    };

    private final EventHandler<TestCaseStarted> caseStarted = testcase -> {
        log.debug("Starting scenario {}", testcase.getTestCase().getName());

        currentScenarioId = testcase.getTestCase().getId().toString();
        final Feature feature = uriFeatureMap.get(testcase.getTestCase().getUri());
        idFeatureMap.computeIfAbsent(currentScenarioId, id -> feature);

        String scenarioName = testcase.getTestCase().getName();
        Scenario scenario = feature.getScenario(scenarioName, testcase.getTestCase().getLocation().getLine());
        scenario.setId(currentScenarioId);
        scenarioStepsMap.computeIfAbsent(currentScenarioId, id -> scenario.getSteps());

        if (feature.getBackground() != null) {
            scenarioStepsMap.get(currentScenarioId).addAll(0, feature.getBackground().getSteps());
        }

        processDataVariantsForScenarioOutlines(scenario);
        informWorkflowUiAboutCurrentScenario(feature, scenario);

        currentStepIndex = 0;
        LocalProxyRbelMessageListener.clearMessages();
    };


    private void informWorkflowUiAboutCurrentScenario(Feature feature, Scenario scenario) {
        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(Map.of(feature.getName(),
                    FeatureUpdate.builder()
                        .description(feature.getName())
                        .scenarios(
                            new LinkedHashMap<>(Map.of(
                                mapScenarioToScenarioUpdateMap(scenario),
                                ScenarioUpdate.builder()
                                    .description(scenario.getName())
                                    .variantIndex(currentScenarioDataVariantIndex)
                                    .exampleKeys(currentScenarioDataVariantIndex != -1 ? currentScenarioDataVariantKeys : null)
                                    .exampleList(currentScenarioDataVariantIndex != -1 ?
                                        currentScenarioDataVariant.get(currentScenarioDataVariantIndex) : null)
                                    .steps(mapStepsToStepUpdateMap(scenario.getSteps(),
                                        line -> (scenario instanceof ScenarioOutline && currentScenarioDataVariantIndex != -1) ?
                                            replaceLineWithCurrentDataVariantValues(line) : line
                                    ))
                                    .build()
                            )))
                        .build()
                )))
            .build());
    }

    private String mapScenarioToScenarioUpdateMap(Scenario scenario) {
        if (scenario instanceof ScenarioOutline
            && currentScenarioDataVariantIndex != -1) {
            return (currentScenarioDataVariantIndex + "-" + scenario.getId());
        } else {
            return scenario.getId();
        }
    }

    private String replaceLineWithCurrentDataVariantValues(String line) {
        String parsedLine = line;
        for (String key : currentScenarioDataVariantKeys) {
            parsedLine = parsedLine.replace("<" + key + ">",
                currentScenarioDataVariant.get(currentScenarioDataVariantIndex).get(key));
        }
        return parsedLine;
    }

    @NotNull
    private Map<String, StepUpdate> mapStepsToStepUpdateMap(List<Step> steps, Function<String, String> postProduction) {
        Map<String, StepUpdate> map = new HashMap<>();
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            if (map.put(Integer.toString(stepIndex), StepUpdate.builder()
                .description(postProduction.apply(String.join("\n", steps.get(stepIndex).getLines())))
                .status(TestResult.PENDING)
                .stepIndex(stepIndex)
                .build()) != null) {
                throw new IllegalStateException("Duplicate key");
            }
        }
        return map;
    }

    private void processDataVariantsForScenarioOutlines(
        de.gematik.test.tiger.lib.parser.model.gherkin.Scenario tigerScenario) {
        if (tigerScenario instanceof ScenarioOutline) {
            if (currentScenarioDataVariantIndex == -1) {
                currentScenarioDataVariant = ((ScenarioOutline) tigerScenario).getExamplesAsList();
                currentScenarioDataVariantKeys = ((ScenarioOutline) tigerScenario).getExampleKeys();
                currentScenarioDataVariantIndex = 0;
            } else {
                currentScenarioDataVariantIndex++;
            }
        } else {
            currentScenarioDataVariantIndex = -1;
            currentScenarioDataVariant = null;
        }
    }

    private final EventHandler<TestStepStarted> stepStarted = event -> {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) {
            return;
        }
        log.debug("Processing step {}", currentStepIndex);
        if (TigerDirector.getLibConfig().isActivateWorkflowUi()) {
            Step currentStep = getCurrentStep(currentScenarioId, currentStepIndex);
            String stepText = String.join("\n", currentStep.getLines());
            Matcher m = showSteps.matcher(stepText);
            if (m.find()) {
                TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(TigerStatusUpdate.builder()
                    .bannerColor(m.group(2))
                    .bannerMessage(m.group(4))
                    .build());
            }
            if (stepText.endsWith("TGR warte auf Abbruch") || stepText.endsWith("TGR wait for user abort")) {
                // TODO notify workflow ui for next step button
                // Eigentlich doch obsolete oder?
            }
        }
        currentStepIndex++;
    };

    private final EventHandler<TestStepFinished> stepFinished = event -> {
        if (!(event.getTestStep() instanceof PickleStepTestStep)) {
            return;
        }
        log.debug("Finished step {}", currentStepIndex);

        if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()
            && TigerDirector.isSerenityAvailable()
            && TigerDirector.curlLoggingFilter != null) {
            TigerDirector.curlLoggingFilter.printToReport();
        }
        informWorkflowUiAboutCurrentStep(currentScenarioId, event.getResult().getStatus().name());
    };


    private Step getCurrentStep(String scenarioId, Integer stepIndex) {
        Step currentStep = scenarioStepsMap.get(scenarioId).get(stepIndex);
        if (currentScenarioDataVariantIndex != -1) {
            List<String> parsedLines = currentStep.getLines().stream().map(
                this::replaceLineWithCurrentDataVariantValues
            ).collect(Collectors.toList());
            currentStep = new Step(currentStep.getKeyword(), parsedLines);
        }
        return currentStep;
    }


    private void informWorkflowUiAboutCurrentStep(String scenarioId, String status) {
        Feature feature = idFeatureMap.get(currentScenarioId);
        Scenario scenario = feature.getScenarioById(scenarioId);

        Step currentStep = getCurrentStep(scenarioId, currentStepIndex - 1);

        TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

        Matcher m = showSteps.matcher(currentStep.getLines().get(0));
        if (m.find()) {
            Color col;
            try {
                if (!m.group(2).trim().isEmpty()) {
                    col = (Color) Color.class.getDeclaredField(
                        RbelAnsiColors.seekColor(m.group(2).trim()).name().toUpperCase()).get(null);
                } else {
                    col = Color.BLACK;
                }
            } catch (Exception ignored) {
                col = Color.BLACK;
            }
            builder.bannerMessage(m.group(4)).bannerColor(String.format("#%06X", (0xFFFFFF & col.getRGB())));
        }

        List<MessageMetaDataDto> stepMessagesMetaDataList = new ArrayList<>(LocalProxyRbelMessageListener.getStepRbelMessages()).stream()
            .map(MessageMetaDataDto::createFrom)
            .collect(Collectors.toList());
        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(builder
            .featureMap(
                new LinkedHashMap<>(Map.of(feature.getName(), FeatureUpdate.builder()
                    .description(feature.getName())
                    .scenarios(
                        new LinkedHashMap<>(Map.of(
                            mapScenarioToScenarioUpdateMap(scenario),
                            ScenarioUpdate.builder()
                                .description(scenario.getName())
                                .variantIndex(currentScenarioDataVariantIndex)
                                .exampleKeys(
                                    currentScenarioDataVariantIndex != -1 ? currentScenarioDataVariantKeys : Collections.emptyList())
                                .exampleList(currentScenarioDataVariantIndex != -1 ? currentScenarioDataVariant.get(
                                    currentScenarioDataVariantIndex) : Collections.emptyMap())
                                .steps(new HashMap<>(Map.of(String.valueOf(currentStepIndex - 1), StepUpdate.builder()
                                    .description(String.join("\n", currentStep.getLines()))
                                    .status(TestResult.valueOf(status))
                                    .stepIndex(currentStepIndex - 1)
                                    .rbelMetaData(stepMessagesMetaDataList)
                                    .build()
                                ))).build()
                        ))).build()
                )))
            .build());
        LocalProxyRbelMessageListener.getStepRbelMessages().clear();

    }

    private final EventHandler<TestCaseFinished> caseFinished = testcase -> {
        log.info("Finished scenario {}", testcase.getTestCase().getName());

        String scenarioName = testcase.getTestCase().getName();
        URI scenarioUri = testcase.getTestCase().getUri();
        String scenarioStatus = testcase.getResult().getStatus().toString();

        // dump overall status for updates while test is still running
        switch (scenarioStatus) {
            case "PASSED":
                scPassed++;
                break;
            case "FAILED":
                scFailed++;
                break;
        }
        log.info("------------ STATUS: {} passed {}", scPassed, scFailed > 0 ? scFailed + " failed" : "");

        createRbelLogReport(scenarioName, scenarioUri);

        scenarioStepsMap.remove(currentScenarioId);

        // if this is the last entry -> reset so that next scenario outline parses new data in @Before
        if (currentScenarioDataVariantIndex != -1 && currentScenarioDataVariantIndex + 1 == currentScenarioDataVariant.size()) {
            currentScenarioDataVariantIndex = -1;
        }
    };

    private void createRbelLogReport(String scenarioName, URI scenarioUri) {
        try {
            // make sure target/rbellogs folder exists
            final File folder = Paths.get("target", "rbellogs").toFile();
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw new TigerOsException("Unable to create folder '" + folder.getAbsolutePath() + "'");
                }
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
                for (Entry<String, String> entry : currentScenarioDataVariant.get(
                    currentScenarioDataVariantIndex).entrySet()) {
                    modal.append("<tr><th>").append(entry.getKey()).append("</th><td>").append(entry.getValue())
                        .append("</td></tr>");
                }
                modal.append("    </table></div>\n</div>\n</div>\n");

                if (bulmaModalJsScript == null) {
                    bulmaModalJsScript = IOUtils.toString(getClass().getResourceAsStream("/js/bulma-modal.js"),
                        StandardCharsets.UTF_8);
                }
                html = html.substring(0, html.indexOf("</html>")) +
                    "<script>" + bulmaModalJsScript + "</script>" + modal + "</html>";
            }
            String name = getFileNameFor(scenarioName, currentScenarioDataVariantIndex);
            final File logFile = Paths.get("target", "rbellogs", name).toFile();
            FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
            if (TigerDirector.isSerenityAvailable()) {
                (Serenity.recordReportData().asEvidence()
                    .withTitle("RBellog " + (currentScenarioDataVariantIndex + 1))).downloadable()
                    .fromFile(logFile.toPath());
            }
            log.info("Saved HTML report of scenario '{}' to {}", scenarioName, logFile.getAbsolutePath());
        } catch (final Exception e) {
            log.error("Unable to create/save rbel log for scenario " + scenarioName, e);
        }
    }

    @NotNull
    public String getFileNameFor(String scenarioName, int dataVariantIndex) {
        String name = scenarioName;
        final String map = "äaÄAöoÖOüuÜUßs _(_)_[_]_{_}_<_>_|_$_%_&_/_\\_?_:_*_\"_";
        for (int i = 0; i < map.length(); i += 2) {
            name = name.replace(map.charAt(i), map.charAt(i + 1));
        }
        if (name.length() > 100) { // Serenity can not deal with longer filenames
            name = name.substring(0, 60) + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        }
        if (dataVariantIndex != -1) {
            name = name + "_" + (dataVariantIndex + 1);
        }
        return name + ".html";
    }
}
