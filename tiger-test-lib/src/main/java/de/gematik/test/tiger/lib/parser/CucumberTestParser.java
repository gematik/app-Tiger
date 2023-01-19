/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.lib.parser;

import de.gematik.test.tiger.lib.parser.model.Testcase;
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
        final var feature = new FeatureParser().parseFeatureFile(f);
        feature.getScenarios()
            .forEach(ch -> {
                    final var tc = new Testcase();
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
                    if (!ref.get()) {
                        unreferencedTestcases.putIfAbsent(tc.getClazz() + ":" + tc.getMethod(), tc);
                    }
                }
            );
        log.info("      Found " + feature.getScenarios().size() + " scenarios in " + f.getAbsolutePath());
    }

    private String convertToId(String name) {
        final var chars = " ;,.+*~\\/!$()[]{}";
        for (var i = 0; i < chars.length(); i++) {
            name = name.replace(chars.charAt(i), '-');
        }
        return name.toLowerCase();
    }

    @Override
    public Map<String, Testcase> getTestcasesWithoutAfo() {
        return unreferencedTestcases;
    }
}



























