/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package io.cucumber.junit;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;

public final class ExamplesResolver {

  public static Optional<ExampleValues> findExampleValues(TestSource testSource) {
    if (testSource instanceof FileSource fileSource) {
      return findExampleValues(
          fileSource, fileSource.getPosition().map(FilePosition::getLine).orElse(0));
    } else if (testSource instanceof ClasspathResourceSource classpathResourceSource) {
      return findExampleValues(
          testSource, classpathResourceSource.getPosition().map(FilePosition::getLine).orElse(0));
    } else {
      return Optional.empty();
    }
  }

  public static Optional<ExampleValues> findExampleValues(TestSource featurePath, int rowLine) {
    var doc = parseDoc(featurePath);
    var featureOpt = doc.flatMap(GherkinDocument::getFeature);

    if (featureOpt.isEmpty()) return Optional.empty();
    if (rowLine <= 0) return Optional.empty();

    var examples =
        allScenariosOutlines(featureOpt.get()).stream()
            .map(Scenario::getExamples)
            .flatMap(List::stream)
            .filter(
                ex ->
                    ex.getTableBody().stream()
                        .anyMatch(tb -> tb.getLocation().getLine() == rowLine))
            .findAny();

    if (examples.isPresent() && examples.get().getTableHeader().isPresent()) {
      var headers =
          examples.get().getTableHeader().get().getCells().stream()
              .map(TableCell::getValue)
              .toList();
      var values = getMatchingValues(examples.get(), rowLine);

      int n = Math.min(headers.size(), values.getRight().size());

      Map<String, String> headerToValues = new LinkedHashMap<>();
      for (int i = 0; i < n; i++) {
        var key = headers.get(i);
        var val = values.getRight().get(i);
        headerToValues.put(key, val);
      }
      return Optional.of(new ExampleValues(values.getLeft(), headerToValues));
    } else {
      return Optional.empty();
    }
  }

  private static Pair<Integer, List<String>> getMatchingValues(Examples examples, int rowLine) {
    var tableRow =
        examples.getTableBody().stream()
            .filter(row -> row.getLocation().getLine() == rowLine)
            .findAny();
    var index = -1;
    if (tableRow.isPresent()) {
      index = examples.getTableBody().indexOf(tableRow.get());
    }

    return Pair.of(
        index,
        tableRow
            .map(TableRow::getCells)
            .map(List::stream)
            .map(s -> s.map(TableCell::getValue).toList())
            .orElse(Collections.emptyList()));
  }

  private static Optional<GherkinDocument> parseDoc(TestSource testSource) {
    if (testSource instanceof FileSource fs) {
      return parseDoc(fs);
    } else if (testSource instanceof ClasspathResourceSource crs) {
      return parseDoc(crs);
    } else {
      return Optional.empty();
    }
  }

  @SneakyThrows
  private static Optional<GherkinDocument> parseDoc(FileSource fileSource) {
    // Ask Gherkin to emit only the AST (GherkinDocument)
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    var envelope = parser.parse(fileSource.getFile().toPath()).findAny();
    return envelope.flatMap(Envelope::getGherkinDocument);
  }

  @SneakyThrows
  private static Optional<GherkinDocument> parseDoc(ClasspathResourceSource testSource) {
    var parser = GherkinParser.builder().includeSource(false).includePickles(false).build();
    var classLoader = ExamplesResolver.class.getClassLoader();
    try (var in = classLoader.getResourceAsStream(testSource.getClasspathResourceName())) {
      if (in == null) {
        return Optional.empty();
      }
      var envelope = parser.parse(testSource.getClasspathResourceName(), in).findFirst();
      return envelope.flatMap(Envelope::getGherkinDocument);
    }
  }

  public static List<Scenario> allScenariosOutlines(Feature feature) {
    List<Scenario> scenarios = new ArrayList<>();
    for (FeatureChild child : feature.getChildren()) {
      child.getScenario().ifPresent(scenarios::add);
      child.getRule().ifPresent(rule -> scenarios.addAll(getRuleChildren(rule)));
    }
    return scenarios.stream().filter(s -> !s.getExamples().isEmpty()).toList();
  }

  private static Collection<Scenario> getRuleChildren(Rule rule) {
    List<Scenario> scenarios = new ArrayList<>();
    rule.getChildren().forEach(child -> child.getScenario().ifPresent(scenarios::add));
    return scenarios;
  }

  public record ExampleValues(int variantIndex, Map<String, String> values) {}
}
