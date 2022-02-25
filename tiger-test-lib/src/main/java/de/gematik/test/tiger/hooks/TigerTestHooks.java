/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibConfig;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.TestParserException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.lib.reports.RestAssuredLogToCurlCommandParser;
import de.gematik.test.tiger.lib.reports.SerenityReportUtils;
import de.gematik.test.tiger.lib.reports.TigerRestAssuredCurlLoggingFilter;
import io.cucumber.java.*;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.rest.SerenityRest;
import org.apache.commons.io.FileUtils;

/**
 * This class integrates SerenityBDD and the Tiger test framework.
 * <p>
 * Initializes Tiger (reading tiger.yaml, starting Tiger test environment manager, local proxy and optionally monitoring UI)
 * <p>
 * Provides and Manages a NON thread safe RbelMessageProvider with two lists. One for reuse by Tiger validation steps and one internal for
 * later usage.
 * <p>
 * Forwards all steps to Monitoring UI, applying data variant substitution
 * <p>
 * At the end of each scenario dumps all rbel messages to the file system and attaches it to the SerenityBDD report as test evidence.
 * <p>
 * <b>ATTENTION!</b> As of now Tiger does not support collecting Rbel messages in a "thread safe" way,
 * so that messages sent in parallel test execution scenarios are tracked. If you do run Tiger in parallel test execution, you must deal
 * with concurrency of RBel messages yourself.
 */
@SuppressWarnings("unused")
@Slf4j
public class TigerTestHooks {

    /**
     * internal flag whether Tiger has been initialized already.
     */
    private static boolean tigerInitialized = false;

    /**
     * List of messages received via local Tiger Proxy. You may clear/manipulate this list if you know what you do. It is used by the TGR
     * validation steps. The list is not cleared at the end of / start of new scenarios!
     * TODO add test to ensure this statement
     */
    @Getter
    private static final List<RbelElement> validatableRbelMessages = new ArrayList<>();

    /**
     * list of messages received from local Tiger Proxy and used to create the RBelLog HTML page and SerenityBDD test report evidence. This
     * list is internal and not accessible to validation steps or Tiger users
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
     * internal flag to ensure the Rbel message listener is added only once to the local Tiger Proxy.
     */
    private static boolean rbelListenerAdded = false;


    /**
     * map of features parsed, identified by their URI.
     */
    private static final Map<URI, Feature> uriFeatureMap = new HashMap<>();
    /**
     * map of steps for each scenario, identified by the scenario id.
     */
    private static final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();
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
     * For scenario outlines, this list is the list of data variant maps. The map contains the value for the specific variant identified by
     * its key.
     */
    private static List<Map<String, String>> currentDataVariant = null;

    /**
     * Tiger test lib configuration as read from tiger.yaml|yml.
     */
    private static TigerLibConfig config;

    private static Optional<TigerRestAssuredCurlLoggingFilter> curlLoggingFilter = Optional.empty();

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
                scenarioStepsMap.get(scenario.getId()).addAll(0, feature.getBackground().getSteps());
            }

            processDataVariantsForScenarioOutlines(feature.getScenario(scenario.getName()));

            currentStepIndex = 0;
        } else {
            log.warn("No scenario has been provided, running in no cucumber mode!");
            currentStepIndex = -1;
        }
    }

    private void initializeTiger() {
        TigerDirector.readConfiguration();
        TigerDirector.applyTestLibConfig();
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy();
    }

    public static void registerRestAssuredFilter() {
        if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()) {
            curlLoggingFilter = Optional.of(new TigerRestAssuredCurlLoggingFilter());
            SerenityRest.filters(curlLoggingFilter.get());
        }
    }

    private void processDataVariantsForScenarioOutlines(de.gematik.test.tiger.lib.parser.model.gherkin.Scenario tigerScenario) {
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
     * If monitoring UI is active, each step is forwarded to the monitoring UI. For scenario outlines the step will be first parsed for data
     * variant tags of pattern &lt;key&gt;, substituting it with the current value.
     * <p>
     * Increases the current step index.
     *
     * @param scenario Current scenario. Might be null if used by non BDD technologies (e.g. JUnit/TestNG tests).
     */
    @BeforeStep
    public void forwardToUiMonitor(final Scenario scenario) {
        if (scenario != null && config.isActivateMonitorUI()) {
            Step currentStep = scenarioStepsMap.get(scenario.getId()).get(currentStepIndex);
            if (currentDataVariantIndex != -1) {
                List<String> parsedLines = currentStep.getLines().stream().map(
                    line -> {
                        String parsedLine = line;
                        for (String key : currentDataVariantKeys) {
                            parsedLine = parsedLine.replace("<" + key + ">", currentDataVariant.get(currentDataVariantIndex).get(key));
                        }
                        return parsedLine;
                    }
                ).collect(Collectors.toList());
                currentStep = new Step(currentStep.getKeyword(), parsedLines);
            }
            log.info("CurrentStep: " + String.join("\n", currentStep.getLines()));
            TigerDirector.updateStepInMonitor(currentStep);
        }
        currentStepIndex++;
    }

    @AfterStep
    public synchronized void addRestAssuredRequestsToReport(final Scenario scenario) {
        if (TigerDirector.getLibConfig().isAddCurlCommandsForRaCallsToReport()) {
            curlLoggingFilter.ifPresent(
                curlFilter -> curlFilter.printToReport()
            );
        }
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

        // TODO TGR-294 add currentdatavariantvalues to report in the header section
        try {
            var rbelRenderer = new RbelHtmlRenderer();
            rbelRenderer.setSubTitle(
                "<p><b>" + scenario.getName() + "</b>&nbsp&nbsp;<u>" + (currentDataVariantIndex + 1) + "</u></p>"
                    + "<p><i>" + scenario.getUri() + "</i></p>");
            String html = rbelRenderer.doRender(rbelMessages);
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
            (Serenity.recordReportData().asEvidence().withTitle("RBellog " + (currentDataVariantIndex + 1))).downloadable()
                .fromFile(logFile.toPath());
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


