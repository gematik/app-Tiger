/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.parser;

import de.gematik.test.tiger.parser.model.Testcase;
import de.gematik.test.tiger.parser.model.gherkin.Feature;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CucumberTestParser implements ITestParser {

    private static final String AFO_TOKEN = "@Afo";
    private final Map<String, List<Testcase>> parsedTestcasesPerAfo = new HashMap<>();
    private final Map<String, Testcase> parsedTestcases = new HashMap<>();
    private final Map<String, Testcase> unreferencedTestcases = new HashMap<>();

    @Override
    public void parseDirectory(final File rootDir) {
        if (rootDir == null) {
            log.warn("Invalid test source NULL root dir");
        } else {
            final File[] files = rootDir.listFiles();
            if (files == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Invalid test source root dir %s", rootDir.getAbsolutePath()));
                }
            } else {
                Arrays.stream(files)
                    .filter(f -> f.isFile() && f.getName().endsWith(".feature"))
                    .forEach(this::inspectFile);
                Arrays.stream(files)
                    .filter((File::isDirectory))
                    .forEach(this::parseDirectory);
            }
        }
    }

    private void inspectFile(final File f) {
        final Feature feature = new FeatureParser().parseFeatureFile(f);
        feature.getScenarios()
            .forEach(ch -> {
                    final Testcase tc = new Testcase();
                    tc.setFeatureName(feature.getName());
                    tc.setScenarioName(ch.getName());
                    tc.setClazz(convertToId(feature.getName()));
                    tc.setMethod(convertToId(ch.getName()));
                    tc.setPath(feature.getFileName());
                parsedTestcases.putIfAbsent(tc.getClazz() + ":" + tc.getMethod(), tc);
                    final AtomicReference<Boolean> ref = new AtomicReference<>(false);
                    ch.getTags().stream()
                        .filter(tag -> tag.getName().equals(AFO_TOKEN))
                        .forEach(afotag -> {
                            final String afoid = afotag.getParameter();
                            parsedTestcasesPerAfo.computeIfAbsent(afoid, k -> new ArrayList<>());
                            parsedTestcasesPerAfo.get(afoid).add(tc);
                            ref.set(true);
                        });
                    if (!ref.get().booleanValue()) {
                        unreferencedTestcases.putIfAbsent(tc.getClazz() + ":" + tc.getMethod(), tc);
                    }
                }
            );
        log.info("      Found " + feature.getScenarios().size() + " scenarios in " + f.getAbsolutePath());
    }

    private String convertToId(String name) {
        final String chars = " ;,.+*~\\/!$()[]{}";
        for (int i = 0; i < chars.length(); i++) {
            name = name.replace(chars.charAt(i), '-');
        }
        return name.toLowerCase();
    }

    @Override
    public Map<String, Testcase> getTestcasesWithoutAfo() {
        return unreferencedTestcases;
    }
}



























