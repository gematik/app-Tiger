/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.context.TestContext;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.TestParserException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import io.cucumber.java.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.FileUtils;

@Slf4j
public class Hooks {

    private static final Map<URI, Feature> uriFeatureMap = new HashMap<>();

    private static final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();
    private static final Map<String, Integer> scenarioStepsIdxMap = new HashMap<>();
    private static final Map<String, Status> scenarioStatus = new HashMap<>();

    private static boolean rbelListenerAdded = false;
    private static final List<RbelElement> rbelMessages = new ArrayList<>();
    @Getter
    private static final List<RbelElement> validatableRbelMessages = new ArrayList<>();
    private static final RbelMessageProvider rbelMessageListener = new RbelMessageProvider() {
        @Override
        public void triggerNewReceivedMessage(RbelElement e) {
            rbelMessages.add(e);
            validatableRbelMessages.add(e);
        }
    };

    // TODO check if outlines get called once or multiple times and how their id looks like?
    @Before(order = 100)
    public void loadFeatureFileNResetRbelLog(final Scenario scenario) {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            throw new AssertionError("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!");
        }
        if (!TigerDirector.isInitialized()) {
            TigerDirector.beforeTestRun();
        }
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

        scenarioStepsIdxMap.put(scenario.getId(), 0);
        rbelMessages.clear();
        if (!TigerDirector.isInitialized()) {
            System.setProperty("TIGER_ACTIVE", "1");
            System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersActive.yaml");
            TigerDirector.beforeTestRun();
        }
        if (!rbelListenerAdded && TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy() != null) {
            TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().addRbelMessageListener(rbelMessageListener);
            rbelListenerAdded = true;
        }
    }

    @BeforeStep
    public void beforeStep(final Scenario scenario) {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.error("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!");
            return;
        }
        final int idx = scenarioStepsIdxMap.get(scenario.getId());
        log.info(Ansi.GREEN + Ansi.BOLD +
            "Executing step " + String.join("\r\n", scenarioStepsMap.get(scenario.getId()).get(idx).getLines())
            + Ansi.RESET
        );
    }

    @AfterStep
    public void afterStep(final Scenario scenario) {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.error("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!");
            return;
        }
        final int idx = scenarioStepsIdxMap.get(scenario.getId());
        if (scenario.isFailed()) {
            if (!scenarioStatus.containsKey(scenario.getId())) {
                scenarioStatus.put(scenario.getId(), scenario.getStatus());
                log.info(Ansi.RED + Ansi.BOLD +
                    "Failed @ step " + String.join("\r\n", scenarioStepsMap.get(scenario.getId()).get(idx).getLines())
                    + Ansi.RESET
                );
            }
        }
        scenarioStepsIdxMap.put(scenario.getId(), idx + 1);
    }

    private static final Map<String, List<Scenario>> processedScenarios = new HashMap<>();
    private static int scPassed = 0;
    private static int scFailed = 0;


    @SneakyThrows
    @After
    public void purgeFeatureFileNSaveRbelLog(final Scenario scenario) {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.error("TIGER_ACTIVE is not set to '1'. ABORTING Tiger hook!");
            return;
        }
        scenarioStepsMap.remove(scenario.getId());

         switch (scenario.getStatus()) {
            case PASSED:
                scPassed++;
                break;
            case FAILED:
                scFailed++;
                break;
        }
        if (scFailed > 0) {
            log.error("------------ STATUS: {} passed  {} failed", scPassed, scFailed);
        } else {
            log.info("------------ STATUS: {} passed", scPassed);
        }

        final File folder = Paths.get("target", "rbellogs").toFile();
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                assertThat(folder).exists();
            }
        }
        final String scenarioId = scenario.getUri().toString() + ":" + scenario.getName();
        processedScenarios.computeIfAbsent(scenarioId, uri -> new ArrayList<>());
        final int dataVariantIdx = processedScenarios.get(scenarioId).size();
        processedScenarios.get(scenarioId).add(scenario);

        var rbelRenderer =  new RbelHtmlRenderer();
        rbelRenderer.setSubTitle(
            "<p><b>" + scenario.getName() + "</b>&nbsp&nbsp;<u>" + (dataVariantIdx + 1) + "</u></p>"
                + "<p><i>" + scenario.getUri() + "</i></p>");
        String html = rbelRenderer.doRender(rbelMessages);
        try {
            String name = scenario.getName();
            final String map = "äaÄAöoÖOüuÜUßs _(_)_[_]_{_}_<_>_|_$_%_&_/_\\_?_:_*_\"_";
            for (int i = 0; i < map.length(); i += 2) {
                name = name.replace(map.charAt(i), map.charAt(i + 1));
            }
            if (name.length() > 100) { // Serenity can not deal with longer filenames
                name = name.substring(0, 60) + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
            }
            if (dataVariantIdx > 0) {
                name = name + "_" + (dataVariantIdx + 1);
            }
            final File logFile = Paths.get("target", "rbellogs", name + ".html").toFile();
            FileUtils.writeStringToFile(logFile, html, StandardCharsets.UTF_8);
            (Serenity.recordReportData().asEvidence().withTitle("RBellog " + (dataVariantIdx + 1))).downloadable()
                .fromFile(logFile.toPath());
            log.info("Saved HTML report to " + logFile.getAbsolutePath());
        } catch (final IOException e) {
            log.error("Unable to save rbel log for scenario " + scenario.getName());
        }
    }
}


