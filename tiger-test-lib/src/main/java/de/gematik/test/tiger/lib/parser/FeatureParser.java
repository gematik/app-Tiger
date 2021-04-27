package de.gematik.test.tiger.lib.parser;

import de.gematik.test.tiger.lib.parser.model.gherkin.*;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

public class FeatureParser {

    public synchronized Feature parseFeatureFile(final File f) {
        return parseFeatureFile(f.toURI());
    }

    public synchronized Feature parseFeatureFile(final URI featureURI) {
        final AtomicReference<Feature> feature = new AtomicReference<>();
        final AtomicReference<Scenario> child = new AtomicReference<>();
        final StringBuilder description = new StringBuilder();
        final List<Tag> tags = new ArrayList<>();
        Step step = null;

        int linectr = 0;
        String excLine = "";

        try {
            ParseMode mode = ParseMode.PRESTRUCT;
            String content = IOUtils.toString(featureURI, StandardCharsets.UTF_8);
            content = content.replace("\r\n", "\n").replace("\r", "\n");
            boolean docString = false;
            final String[] lines = content.split("\n");
            for (String line : lines) {
                excLine = line;
                linectr++;
                line = line.trim();

                if (line.startsWith("\"\"\"") || line.startsWith("'''")) {
                    docString = !docString;
                }
                if (docString) {
                    if (step == null) {
                        throw new TestParserException("Step not set!");
                    }
                    step.getLines().add(excLine);
                } else if (line.startsWith("#") || line.isBlank() || parseTagsFromLine(line, tags)) {
                    // skip comments
                } else if (parseGherkinStructFromLine(line, tags, feature, child)) {
                    mode = ParseMode.DESCRIPTION;
                } else {
                    final AtomicReference<ParseMode> moderef = new AtomicReference<>(mode);
                    step = getStep(child, description, step, line, excLine, moderef);
                    mode = moderef.get();
                }
            }

            feature.get().setFileName(new File(featureURI).getAbsolutePath());
            return feature.get();
        } catch (final Exception e) {
            throw new TestParserException(
                String.format("Error in line %d '%s' of file '%s'",
                    linectr, excLine, new File(featureURI).getAbsolutePath()), e);
        }
    }

    private Step getStep(final AtomicReference<Scenario> child, final StringBuilder description,
        Step step, final String line, final String origLine, final AtomicReference<ParseMode> moderef) {
        final String keyword = Step.getKeyword(line);
        if (moderef.get() == ParseMode.DESCRIPTION) {
            if (!Step.KEYWORDS.contains(keyword)) {
                if (!line.equals("```")) {
                    description.append(line).append("\n");
                }
            } else {
                child.get().setDescription(description.toString());
                description.setLength(0);
                step = Step.fromLine(line);
                moderef.set(addStepToScenario(child.get(), step));
            }
        } else if (moderef.get() == ParseMode.STEPS || moderef.get() == ParseMode.EXAMPLES) {
            if (Step.KEYWORDS.contains(keyword)) {
                step = Step.fromLine(line);
                moderef.set(addStepToScenario(child.get(), step));
            } else {
                if (line.equals("\"\"\"") || line.equals("'''")) {
                    step.getLines().add(origLine);
                } else {
                    step.getLines().add(line);
                }
            }
        } else {
            throw new TestParserException("Unable to parse line");
        }
        return step;
    }

    private ParseMode addStepToScenario(final Scenario sc, final Step step) {
        if (step.getKeyword().equals("Examples")) {
            if (sc instanceof ScenarioOutline) {
                ((ScenarioOutline) sc).setExamples(step);
                return ParseMode.EXAMPLES;
            } else {
                throw new TestParserException(
                    "Unable to add Examples section to Scenario '" + sc.getName() + "' which is not an outline");
            }
        } else {
            sc.getSteps().add(step);
            return ParseMode.STEPS;
        }
    }

    private boolean parseTagsFromLine(final String line, final List<Tag> tags) {
        if (line.startsWith("@")) {
            Arrays.stream(line.split(" "))
                .map(Tag::fromString)
                .forEach(tags::add);
            return true;
        }
        return false;
    }


    @SneakyThrows
    private boolean parseGherkinStructFromLine(final String line, final List<Tag> tags,
        final AtomicReference<Feature> feature, final AtomicReference<Scenario> child) {
        final int colon = line.indexOf(':');
        if (colon != -1) {
            final String structName = line.substring(0, colon).replace(" ", "").replace("\t", "");
            if (!GherkinStruct.STRUCT_NAMES.contains(structName)) {
                return false;
            }
            final GherkinStruct gs = (GherkinStruct) Class
                .forName(getClass().getPackageName() + ".model.gherkin." + structName)
                .getConstructor()
                .newInstance();
            gs.setName(line.substring(colon + 1).trim());
            gs.getTags().addAll(tags);
            tags.clear();

            Optional.of(gs)
                .filter(Feature.class::isInstance)
                .map(Feature.class::cast)
                .ifPresentOrElse(
                    feature::set,
                    () -> {
                        final Scenario sc;
                        if (gs instanceof Scenario) {
                            sc = (Scenario) gs;
                        } else {
                            throw new TestParserException("Unknown Gherkin struct " + gs.getName());
                        }
                        child.set(sc);
                        sc.setFeature(feature.get());
                        if (sc instanceof Background) {
                            feature.get().setBackground((Background) sc);
                        } else {
                            feature.get().getScenarios().add(sc);
                        }
                    });

            return true;
        }
        return false;
    }
}
