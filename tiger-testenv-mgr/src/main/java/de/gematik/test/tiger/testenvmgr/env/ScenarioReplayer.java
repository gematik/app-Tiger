/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.controller.ReplayController;
import de.gematik.test.tiger.testenvmgr.controller.ReplayableFeature;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.runtime.FeatureSupplier;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class ScenarioReplayer {

  @Setter private io.cucumber.core.runtime.Runtime runtime;

  @SneakyThrows
  public void rerunTest(ReplayController.ScenarioIdentifier scenarioLocation) {
    List<Feature> features =
        ((FeatureSupplier) FieldUtils.readField(runtime, "featureSupplier", true)).get();

    List<Pickle> matchingPickles =
        features.stream()
            .filter(f -> f.getUri().equals(scenarioLocation.getScenarioUri()))
            .flatMap(f -> f.getPickles().stream())
            .filter(p -> doesLocationMatch(p, scenarioLocation))
            .toList();

    if (matchingPickles.isEmpty()) {
      throw new NoSuchElementException(
          "Failed to find a Scenario matching the given identifier: " + scenarioLocation);
    }

    Pickle toReplay;
    if (scenarioLocation.isScenarioOutline()) {
      toReplay = matchingPickles.get(scenarioLocation.getVariantIndex());
    } else {
      toReplay = matchingPickles.get(0);
    }

    createFeatureWithSinglePickle(features, toReplay).ifPresent(this::replayFeature);
  }

  private boolean doesLocationMatch(
      Pickle pickle, ReplayController.ScenarioIdentifier scenarioLocation) {
    if (scenarioLocation.isScenarioOutline()) {
      return pickle.getScenarioLocation().equals(scenarioLocation.getLocation());
    } else {
      return pickle.getLocation().equals(scenarioLocation.getLocation());
    }
  }

  @SneakyThrows
  private synchronized void replayFeature(Feature feature) {
    MethodUtils.invokeMethod(runtime, true, "runFeatures", List.of(feature));
  }

  private Optional<Feature> createFeatureWithSinglePickle(List<Feature> features, Pickle pickle) {
    for (Feature feature : features) {
      Optional<Pickle> matchingPickle =
          feature.getPickles().stream().filter(p -> p.equals(pickle)).findAny();
      if (matchingPickle.isPresent()) {
        return Optional.of(
            new ReplayableFeature(feature, UUID.fromString(matchingPickle.get().getId())));
      }
    }
    return Optional.empty();
  }
}
