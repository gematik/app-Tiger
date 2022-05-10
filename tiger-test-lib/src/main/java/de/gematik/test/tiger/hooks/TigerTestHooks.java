/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.TestParserException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.lib.reports.TigerRestAssuredCurlLoggingFilter;
import de.gematik.test.tiger.testenvmgr.env.*;
import io.cucumber.java.*;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
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
import net.serenitybdd.rest.SerenityRest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

/**
 * This class integrates SerenityBDD and the Tiger test framework.
 * <p>
 * Initializes Tiger (reading tiger.yaml, starting Tiger test environment manager, local proxy and optionally monitoring
 * UI)
 * <p>
 * Provides and Manages a NON thread safe RbelMessageProvider with two lists. One for reuse by Tiger validation steps
 * and one internal for later usage.
 * <p>
 * Forwards all steps to Monitoring UI, applying data variant substitution
 * <p>
 * At the end of each scenario dumps all rbel messages to the file system and attaches it to the SerenityBDD report as
 * test evidence.
 * <p>
 * <b>ATTENTION!</b> As of now Tiger does not support collecting Rbel messages in a "thread safe" way,
 * so that messages sent in parallel test execution scenarios are tracked. If you do run Tiger in parallel test
 * execution, you must deal with concurrency of RBel messages yourself.
 */
@SuppressWarnings("unused")
@Slf4j
public class TigerTestHooks {

    /**
     * List of messages received via local Tiger Proxy. You may clear/manipulate this list if you know what you do. It
     * is used by the TGR validation steps. The list is not cleared at the end of / start of new scenarios!
     * TODO add test to ensure this statement
     */
    @Getter
    private static final List<RbelElement> validatableRbelMessages = new ArrayList<>();

    /**
     * list of messages received from local Tiger Proxy and used to create the RBelLog HTML page and SerenityBDD test
     * report evidence. This list is internal and not accessible to validation steps or Tiger users
     */
    private static final List<RbelElement> rbelMessages = new ArrayList<>();

    /**
     * simple implementation of the RBelMessageProvider collecting all messages in two separate lists.
     */
    private static final RbelMessageProvider rbelMessageListener = new RbelMessageProvider() {
        @Override
        public void triggerNewReceivedMessage(RbelElement e) {
            rbelMessages.add(e);
            validatableRbelMessages.add(e);
        }
    };
    /**
     * map of features parsed, identified by their URI.
     */
    private static final Map<URI, Feature> uriFeatureMap = new HashMap<>();
    /**
     * map of steps for each scenario, identified by the scenario id.
     */
    private static final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();
    /**
     * internal flag whether Tiger has been initialized already.
     */
    private static boolean tigerInitialized = false;
    /**
     * internal flag to ensure the Rbel message listener is added only once to the local Tiger Proxy.
     */
    private static boolean rbelListenerAdded = false;
    /**
     * index of currently executed step.
     */
    private static int currentStepIndex;

    /**
     * number of passed scenarios / scenario data variants.
     */
    private static int scPassed = 0;
    /**
     * number of failed scenarios / scenario data variants.
     */
    private static int scFailed = 0;

    /**
     * For scenario outlines, this index is used to identify the current data variant under execution.
     */
    private static int currentDataVariantIndex = -1;
    /**
     * For scenario outlines, this list is the list of keys in the example section.
     */
    private static List<String> currentDataVariantKeys = null;
    /**
     * For scenario outlines, this list is the list of data variant maps. The map contains the value for the specific
     * variant identified by its key.
     */
    private static List<Map<String, String>> currentDataVariant = null;

    private static String bulmaModalJSScript = null;

    private static TigerRestAssuredCurlLoggingFilter curlLoggingFilter;

    public static synchronized void registerRestAssuredFilter() {
        if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport() && curlLoggingFilter == null) {
            curlLoggingFilter = new TigerRestAssuredCurlLoggingFilter();
            SerenityRest.filters(curlLoggingFilter);
        }
    }

    public static synchronized void unregisterRestAssuredFilter() {
        if (curlLoggingFilter != null) {
            SerenityRest.replaceFiltersWith(new ArrayList<>());
        }
        curlLoggingFilter = null;
    }

    /**
     * Initializes Tiger and rbel message listener and parses feature file and scenario steps.
     * <p>
     * For scenario outlines the data variants are also parsed from the examples section.
     *
     * @param scenario Current scenario. Might be null if used by non BDD technologies (e.g. JUnit/TestNG tests).
     */
    @Before(order = 100)
    public void parseFeatureFileAndResetRbelLog(final Scenario scenario) {
        if (!tigerInitialized) {
            tigerInitialized = true;
            initializeTiger();
        }

        if (!rbelListenerAdded && TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy() != null) {
            TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().addRbelMessageListener(rbelMessageListener);
            rbelListenerAdded = true;
        }

        rbelMessages.clear();

        if (scenario != null) {
            final Feature feature = uriFeatureMap
                .computeIfAbsent(scenario.getUri(), uri -> new FeatureParser().parseFeatureFile(uri));

            scenarioStepsMap.computeIfAbsent(scenario.getId(), id -> feature.getScenarios().stream()
                .filter(sc -> sc.getName().equals(scenario.getName()))
                .map(de.gematik.test.tiger.lib.parser.model.gherkin.Scenario.class::cast)
                .map(de.gematik.test.tiger.lib.parser.model.gherkin.Scenario::getSteps)
                .findAny()
                .orElseThrow(() -> new TestParserException(
                    String.format("Unable to obtain test steps for scenario %s in feature file %s",
                        scenario.getName(), scenario.getUri()))));

            if (feature.getBackground() != null) {
                if (!(scenarioStepsMap.get(scenario.getId()).containsAll(feature.getBackground().getSteps()))) {
                    scenarioStepsMap.get(scenario.getId()).addAll(0, feature.getBackground().getSteps());
                }
            }

            processDataVariantsForScenarioOutlines(feature.getScenario(scenario.getName(), scenario.getLine()));
            informWorkflowUiAboutCurrentScenario(feature, scenario.getName(), scenario.getLine());

            currentStepIndex = 0;
        } else {
            log.warn("No scenario has been provided, running in no cucumber mode!");
            currentStepIndex = -1;
        }
    }


    private void informWorkflowUiAboutCurrentScenario(Feature feature, String scenarioName, Integer scenarioLine) {
        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(TigerStatusUpdate.builder()
            .featureMap(
                new LinkedHashMap<>(Map.of(feature.getName(), FeatureUpdate.builder()
                    .description(feature.getName())
                    .scenarios(
                        new LinkedHashMap<>(Map.of(
                            mapScenarioToScenarioUpdateMap(feature, scenarioName, scenarioLine),
                            ScenarioUpdate.builder()
                                .description(scenarioName)
                                .variantIndex(currentDataVariantIndex)
                                .exampleKeys(currentDataVariantIndex != -1 ? currentDataVariantKeys : null)
                                .exampleList(currentDataVariantIndex != -1 ? currentDataVariant.get(currentDataVariantIndex) : null)
                                .steps(mapStepsToStepUpdateMap(feature.getScenario(scenarioName, scenarioLine).getSteps(),
                                    line -> {
                                        if (feature.getScenario(scenarioName, scenarioLine) instanceof ScenarioOutline
                                            && currentDataVariantIndex != -1) {
                                            return replaceLineWithCurrentDataVariantValues(line);
                                        } else {
                                            return line;
                                        }
                                    })).build()
                        ))).build()
                ))).build());
    }

    private String mapScenarioToScenarioUpdateMap(Feature feature, String scenarioName, Integer scenarioLine) {
        if (feature.getScenario(scenarioName, scenarioLine) instanceof ScenarioOutline
            && currentDataVariantIndex != -1) {
            return (currentDataVariantIndex + "-" + scenarioName);
        } else {
            return scenarioName;
        }
    }

    private String replaceLineWithCurrentDataVariantValues(String line) {
        String parsedLine = line;
        for (String key : currentDataVariantKeys) {
            parsedLine = parsedLine.replace("<" + key + ">",
                currentDataVariant.get(currentDataVariantIndex).get(key));
        }
        return parsedLine;
    }

    @NotNull
    private Map<String, StepUpdate> mapStepsToStepUpdateMap(List<Step> steps, Function<String, String> postProduction) {
        Map<String, StepUpdate> map = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Integer stepIndex = Integer.valueOf(i);
            if (map.put(stepIndex.toString(), StepUpdate.builder()
                .description(postProduction.apply(String.join("\n", steps.get(stepIndex).getLines())))
                .status(TestResult.PENDING)
                .stepIndex(stepIndex)
                .build()) != null) {
                throw new IllegalStateException("Duplicate key");
            }
        }
        return map;
    }

    private void initializeTiger() {
        TigerGlobalConfiguration.setRequireTigerYaml(true);
        TigerDirector.start();
        registerRestAssuredFilter();
    }

    private void processDataVariantsForScenarioOutlines(
        de.gematik.test.tiger.lib.parser.model.gherkin.Scenario tigerScenario) {
        if (tigerScenario instanceof ScenarioOutline) {
            if (currentDataVariantIndex == -1) {
                currentDataVariant = ((ScenarioOutline) tigerScenario).getExamplesAsList();
                currentDataVariantKeys = ((ScenarioOutline) tigerScenario).getExampleKeys();
                currentDataVariantIndex = 0;
            } else {
                currentDataVariantIndex++;
            }
        } else {
            currentDataVariantIndex = -1;
            currentDataVariant = null;
        }
    }

    /**
     * If monitoring UI is active, each step is forwarded to the monitoring UI. For scenario outlines the step will be
     * first parsed for data variant tags of pattern &lt;key&gt;, substituting it with the current value.
     * <p>
     * Increases the current step index.
     *
     * @param scenario Current scenario. Might be null if used by non BDD technologies (e.g. JUnit/TestNG tests).
     */
    @BeforeStep
    public void forwardToUiMonitor(final Scenario scenario) {
        if (scenario != null && TigerDirector.getLibConfig().isActivateMonitorUI()) {
            Step currentStep = getCurrentStep(scenario, currentStepIndex);
            log.info("CurrentStep: " + String.join("\n", currentStep.getLines()));
            TigerDirector.updateStepInMonitor(currentStep);
        }
        currentStepIndex++;
    }

    private Step getCurrentStep(Scenario scenario, Integer stepIndex) {
        Step currentStep = scenarioStepsMap.get(scenario.getId()).get(stepIndex);
        if (currentDataVariantIndex != -1) {
            List<String> parsedLines = currentStep.getLines().stream().map(
                line -> replaceLineWithCurrentDataVariantValues(line)
            ).collect(Collectors.toList());
            currentStep = new Step(currentStep.getKeyword(), parsedLines);
        }
        return currentStep;
    }

    @AfterStep
    public synchronized void addRestAssuredRequestsToReportAndInformWorkflowUI(final Scenario scenario) {
        if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport() && TigerDirector.isSerenityAvailable()
            && curlLoggingFilter != null) {
            curlLoggingFilter.printToReport();
        }
        informWorkflowUiAboutCurrentStep(scenario);
    }

    private final Pattern showSteps = Pattern.compile(".*TGR (zeige|show) ([\\w|ü|ß| ]*)(Banner|banner|text|Text) \"(.*)\""); //NOSONAR


    private void informWorkflowUiAboutCurrentStep(Scenario scenario) {
        if (scenario == null) {
            return;
        }

        final Feature feature = uriFeatureMap.get(scenario.getUri());
        Step currentStep = getCurrentStep(scenario, currentStepIndex - 1);

        TigerStatusUpdate.TigerStatusUpdateBuilder builder = TigerStatusUpdate.builder();

        Matcher m = showSteps.matcher(currentStep.getLines().get(0));
        if (m.find()) {
            Color col = Color.BLACK;
            try {
                if (!m.group(2).trim().isEmpty()) {
                    col = (Color) Color.class.getDeclaredField(
                        RbelAnsiColors.seekColor(m.group(2).trim()).name().toUpperCase()).get(null);
                }
            } catch (Exception ignored) {
                col = Color.BLACK;
            }
            builder.bannerMessage(m.group(4)).bannerColor(String.format("#%06X", (0xFFFFFF & col.getRGB())));
        }

        TigerDirector.getTigerTestEnvMgr().receiveTestEnvUpdate(builder
            .featureMap(
                new LinkedHashMap<>(Map.of(feature.getName(), FeatureUpdate.builder()
                    .description(feature.getName())
                    .scenarios(
                        new LinkedHashMap<>(Map.of(
                            mapScenarioToScenarioUpdateMap(feature, scenario.getName(), scenario.getLine()),
                            ScenarioUpdate.builder()
                                .description(scenario.getName())
                                .variantIndex(currentDataVariantIndex)
                                .exampleKeys(currentDataVariantIndex != -1 ? currentDataVariantKeys : null)
                                .exampleList(currentDataVariantIndex != -1 ? currentDataVariant.get(currentDataVariantIndex) : null)
                                .steps(new HashMap<>(Map.of(String.valueOf(currentStepIndex - 1), StepUpdate.builder()
                                    .description(String.join("\n", currentStep.getLines()))
                                    .status(TestResult.valueOf(scenario.getStatus().toString()))
                                    .stepIndex(currentStepIndex - 1)
                                    .build()
                                ))).build()
                        ))).build()
                ))).build());

    }

    @After
    public void saveRbelMessagesToFile(final Scenario scenario) {
        // for non BDD integrations there is nothing to do here
        if (scenario == null) {
            return;
        }

        // dump overall status for updates while test is still running
        switch (scenario.getStatus()) {
            case PASSED:
                scPassed++;
                break;
            case FAILED:
                scFailed++;
                break;
        }
        log.info("------------ STATUS: {} passed {}", scPassed, scFailed > 0 ? scFailed + " failed" : "");

        // make sure target/rbellogs folder exists
        final File folder = Paths.get("target", "rbellogs").toFile();
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                assertThat(folder).exists();
            }
        }

        try {
            var rbelRenderer = new RbelHtmlRenderer();
            rbelRenderer.setSubTitle(
                "<p><b>" + scenario.getName() + "</b>&nbsp&nbsp;"
                    + (currentDataVariantIndex != -1 ?
                    "<button class=\"js-modal-trigger\" data-target=\"modal-data-variant\">Variant " + (
                        currentDataVariantIndex + 1) + "</button>" :
                    "")
                    + "</p><p><i>" + scenario.getUri() + "</i></p>");
            String html = rbelRenderer.doRender(rbelMessages);

            if (currentDataVariantIndex != -1) {
                StringBuilder modal = new StringBuilder("<div id=\"modal-data-variant\" class=\"modal\">\n"
                    + "  <div class=\"modal-background\"></div>\n"
                    + "  <div class=\"modal-content\">\n"
                    + "    <div class=\"box\"><h2>Scenario Data</h2><table class=\"table is-striped is-hoverable is-fullwidth\">\n");
                for (Entry<String, String> entry : currentDataVariant.get(currentDataVariantIndex).entrySet()) {
                    modal.append("<tr><th>").append(entry.getKey()).append("</th><td>").append(entry.getValue())
                        .append("</td></tr>");
                }
                modal.append("    </table></div>\n</div>\n</div>\n");

                if (bulmaModalJSScript == null) {
                    bulmaModalJSScript = IOUtils.toString(getClass().getResourceAsStream("/js/bulma-modal.js"),
                        StandardCharsets.UTF_8);
                }
                html = html.substring(0, html.indexOf("</html>")) +
                    "<script>" + bulmaModalJSScript + "</script>" + modal + "</html>";
            }
            String name = scenario.getName();
            final String map = "äaÄAöoÖOüuÜUßs _(_)_[_]_{_}_<_>_|_$_%_&_/_\\_?_:_*_\"_";
            for (int i = 0; i < map.length(); i += 2) {
                name = name.replace(map.charAt(i), map.charAt(i + 1));
            }
            if (name.length() > 100) { // Serenity can not deal with longer filenames
                name = name.substring(0, 60) + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            }
            if (currentDataVariantIndex != -1) {
                name = name + "_" + (currentDataVariantIndex + 1);
            }
            final File logFile = Paths.get("target", "rbellogs", name + ".html").toFile();
            FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
            if (TigerDirector.isSerenityAvailable()) {
                (Serenity.recordReportData().asEvidence()
                    .withTitle("RBellog " + (currentDataVariantIndex + 1))).downloadable()
                    .fromFile(logFile.toPath());
            }
            log.info("Saved HTML report to " + logFile.getAbsolutePath());
        } catch (final IOException e) {
            log.error("Unable to create/save rbel log for scenario " + scenario.getName());
        }

        scenarioStepsMap.remove(scenario.getId());

        // if this is the last entry -> reset so that next scenario outline parses new data in @Before
        if (currentDataVariantIndex != -1 && currentDataVariantIndex + 1 == currentDataVariant.size()) {
            currentDataVariantIndex = -1;
        }
    }
}


