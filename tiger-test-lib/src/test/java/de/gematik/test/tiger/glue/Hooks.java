package de.gematik.test.tiger.glue;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.parser.FeatureParser;
import de.gematik.test.tiger.lib.parser.TestParserException;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import io.cucumber.java.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Hooks {

    private static final Map<URI, Feature> uriFeatureMap = new HashMap<>();

    private static final Map<String, List<Step>> scenarioStepsMap = new HashMap<>();
    private static final Map<String, Integer> scenarioStepsIdxMap = new HashMap<>();
    private static final Map<String, Status> scenarioStatus = new HashMap<>();

    private static boolean rbelListenerAdded = false;
    private static final List<RbelElement> rbelElements = new ArrayList<>();
    private static final IRbelMessageListener rbelMessageListener = new IRbelMessageListener() {
        @Override
        public void triggerNewReceivedMessage(RbelElement el) {
            rbelElements.add(el);
        }
    };

    // TODO check if outlines get called once or multiple times and how their id looks like?
    @Before
    public void loadFeatureFileNResetRbelLog(final Scenario scenario) {
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
        scenarioStepsIdxMap.put(scenario.getId(), 0);

        rbelElements.clear();
        if (!rbelListenerAdded) {
            TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().addRbelMessageListener(rbelMessageListener);
            rbelListenerAdded = true;
        }
    }

    @BeforeStep
    public void beforeStep(final Scenario scenario) {
        final int idx = scenarioStepsIdxMap.get(scenario.getId());
        log.info(Ansi.GREEN + Ansi.BOLD +
            "Executing step " + String.join("\r\n", scenarioStepsMap.get(scenario.getId()).get(idx).getLines())
            + Ansi.RESET
        );
    }

    @AfterStep
    public void afterStep(final Scenario scenario) {
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

    @SneakyThrows
    @After
    public void purgeFeatureFileNSaveRbelLog(final Scenario scenario) {
        scenarioStepsMap.remove(scenario.getId());
        String html = new RbelHtmlRenderer().doRender(rbelElements);
        Path htmlFile = Path.of("rbel-" + scenario.getName() + ".html");
        log.info("Saving rbel log to " + htmlFile.toFile().getAbsolutePath());
        Files.writeString(htmlFile, html);
    }
}


