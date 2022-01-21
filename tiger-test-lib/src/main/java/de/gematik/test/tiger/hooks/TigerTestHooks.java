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
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibConfig;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.TestParserException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import io.cucumber.java.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

@SuppressWarnings("unused")
@Slf4j
public class TigerTestHooks {

    private static boolean initialized = false;

    /**
     * List of messages received via local Tiger Proxy.
     * You may clear/manipulate this list if you know what you do.
     * It is used by the TGR validation steps.
     */
    @Getter
    private static final List<RbelElement> validatableRbelMessages = new ArrayList<>();

    private static boolean rbelListenerAdded = false;
    /** list of messages received from local Tiger Proxy and used to create the RBelLog HTML page.
     * This list is internal and not accessible to validation steps or Tiger users
     */
    private static final List<RbelElement> rbelMessages = new ArrayList<>();
    private static final RbelMessageProvider rbelMessageListener = new RbelMessageProvider() {
        @Override
        public void triggerNewReceivedMessage(RbelElement e) {
            rbelMessages.add(e);
            validatableRbelMessages.add(e);
        }
    };

    private static final Map<URI, Feature> uriFeatureMap = new HashMap<>();
    private static final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();
    private static int currentStepIndex;
    private static final Map<String, Status> scenarioStatus = new HashMap<>();
    private static int scPassed = 0;
    private static int scFailed = 0;

    private static int currentDataVariantIndex = -1;
    private static List<String> currentDataVariantKeys = null;
    private static List<Map<String, String>> currentDataVariant = null;

    @Before(order = 100)
    public void parseFeatureFileAndResetRbelLog(final Scenario scenario) {
        assertTigerActive();
        if (!initialized) {
            initializeTiger();
        }

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
            de.gematik.test.tiger.lib.parser.model.gherkin.Scenario tigerScenario = feature.getScenario(scenario.getName());
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

            currentStepIndex = 0;
        } else {
            log.warn("No scenario has been provided, running in no cucumber mode!");
            currentStepIndex = -1;
        }

        rbelMessages.clear();
        if (!rbelListenerAdded && TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy() != null) {
            TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().addRbelMessageListener(rbelMessageListener);
            rbelListenerAdded = true;
        }
    }

    private void initializeTiger() {
        if (!TigerGlobalConfiguration.readBoolean("TIGER_NOLOGO", false)) {
            try {
                log.info("\n" + IOUtils.toString(
                    Objects.requireNonNull(TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi")),
                    StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new TigerStartupException("Unable to read tiger logo!");
            }
        }

        log.info("\n" + Banner.toBannerStr("READING TIGER LIB CONFIG...", RbelAnsiColors.BLUE_BOLD.toString()));
        File cfgFile = new File("tiger.yml");
        if (!cfgFile.exists()) {
            cfgFile = new File("tiger.yaml");
        }
        TigerLibConfig config;
        if (cfgFile.exists()) {
            try {
                TigerGlobalConfiguration.readFromYaml(FileUtils.readFileToString(cfgFile, StandardCharsets.UTF_8), "TIGER_LIB");
            } catch (IOException e) {
                throw new TigerStartupException("Error while reading configuration file '"+cfgFile.getAbsolutePath(), e);
            }
            config = TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB");
        } else {
            log.warn("No Tiger configuration file found (tiger.yaml, tiger.yml)! Continuing with default values");
            config = new TigerLibConfig();
        }

        if (config.isRbelPathDebugging()) {
            RbelOptions.activateRbelPathDebugging();
        } else {
            RbelOptions.deactivateRbelPathDebugging();
        }
        if (config.isRbelAnsiColors()) {
            RbelOptions.activateAnsiColors();
        } else {
            RbelOptions.deactivateAnsiColors();
        }
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy(config);
        initialized = true;
    }

    @BeforeStep
    public void forwardToUiMonitor(final Scenario scenario) {
        assertTigerActive();
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
        currentStepIndex++;
    }

    @AfterStep
    public void addStatusIfStepFailed(final Scenario scenario) {
        assertTigerActive();
        if (scenario.isFailed()) {
            if (!scenarioStatus.containsKey(scenario.getId())) {
                scenarioStatus.put(scenario.getId(), scenario.getStatus());
            }
        }
    }

    @SneakyThrows
    @After
    public void saveRbelMessagesToFile(final Scenario scenario) {
        assertTigerActive();
        scenarioStepsMap.remove(scenario.getId());

        switch (scenario.getStatus()) {
            case PASSED:
                scPassed++;
                break;
            case FAILED:
                scFailed++;
                break;
        }
        log.info("------------ STATUS: {} passed {}", scPassed, scFailed > 0 ? scFailed + " failed" : "");

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
    }

    public static void assertTigerActive() {
        if (!TigerGlobalConfiguration.readBoolean("TIGER_ACTIVE")) {
            log.error(Ansi.colorize("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!", RbelAnsiColors.RED_BOLD_BRIGHT));
            throw new TigerStartupException("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!");
        }
    }
}


