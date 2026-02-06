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
package io.cucumber.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelMismatchNoteFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.lib.exception.ValidatorAssertionError;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.env.ScenarioUpdate;
import de.gematik.test.tiger.testenvmgr.env.StepUpdate;
import io.cucumber.plugin.event.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import junit.framework.AssertionFailedError;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that mismatch notes from ValidatorAssertionError are forwarded into env status.
 */
class MismatchNotesForwardingTest extends AbstractTigerSerenityEnvStatusTest {

  @Test
  void mismatchNotesForwardedOnFailedStep() {
    publishFeatureSource();
    TestStep failingStep = buildStep(71, 5);
    TestCase testCase = simpleTestCase(failingStep);
    listener.handleTestCaseStarted(new TestCaseStarted(Instant.now(), testCase));

    Pair<RbelElement, RbelMismatchNoteFacet> valueMismatchPair =
        buildMismatchPair(RbelMismatchNoteFacet.MismatchType.VALUE_MISMATCH, 30, "Value mismatch");
    Pair<RbelElement, RbelMismatchNoteFacet> missingNodePair =
        buildMismatchPair(RbelMismatchNoteFacet.MismatchType.MISSING_NODE, 10, "Missing node");
    Pair<RbelElement, RbelMismatchNoteFacet> ambiguousPair =
        buildMismatchPair(RbelMismatchNoteFacet.MismatchType.AMBIGUOUS, 20, "Ambiguous path");

    var map = new LinkedHashMap<RbelElement, SortedSet<RbelMismatchNoteFacet>>();
    map.put(valueMismatchPair.getLeft(), toSortedSet(valueMismatchPair.getRight()));
    map.put(missingNodePair.getLeft(), toSortedSet(missingNodePair.getRight()));
    map.put(ambiguousPair.getLeft(), toSortedSet(ambiguousPair.getRight()));

    var validatorError = new ValidatorAssertionError("Validation failed", map);
    listener.handleTestStepStarted(new TestStepStarted(Instant.now(), testCase, failingStep));
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            failingStep,
            new Result(Status.FAILED, Duration.ofMillis(100), validatorError)));

    TigerEnvStatusDto status = envStatusController.getStatus();
    String scenarioUniqueId = findScenarioUniqueId(testCase);
    ScenarioUpdate scenario =
        status.getFeatureMap().get(featureName).getScenarios().get(scenarioUniqueId);
    StepUpdate stepUpdate = scenario.getSteps().get("0");

    assertThat(stepUpdate.getMismatchNotes()).hasSize(3);
    assertThat(
            stepUpdate.getMismatchNotes().stream()
                .map(RbelMismatchNoteFacet::getMismatchType)
                .toList())
        .isEqualTo(
            List.of(
                RbelMismatchNoteFacet.MismatchType.VALUE_MISMATCH,
                RbelMismatchNoteFacet.MismatchType.MISSING_NODE,
                RbelMismatchNoteFacet.MismatchType.AMBIGUOUS));
  }

  @Test
  void noMismatchNotesForNonValidatorError() {
    publishFeatureSource();
    TestStep failingStep = buildStep(71, 5);
    TestCase testCase = simpleTestCase(failingStep);
    listener.handleTestCaseStarted(new TestCaseStarted(Instant.now(), testCase));
    listener.handleTestStepStarted(new TestStepStarted(Instant.now(), testCase, failingStep));
    listener.handleTestStepFinished(
        new TestStepFinished(
            Instant.now(),
            testCase,
            failingStep,
            new Result(Status.FAILED, Duration.ofMillis(50), new AssertionFailedError("boom"))));

    TigerEnvStatusDto status = envStatusController.getStatus();
    String scenarioUniqueId = findScenarioUniqueId(testCase);
    StepUpdate stepUpdate =
        status
            .getFeatureMap()
            .get(featureName)
            .getScenarios()
            .get(scenarioUniqueId)
            .getSteps()
            .get("0");
    assertThat(stepUpdate.getMismatchNotes()).isEmpty();
  }

  private static SortedSet<RbelMismatchNoteFacet> toSortedSet(RbelMismatchNoteFacet note) {
    var set = new TreeSet<>(RbelMismatchNoteFacet.COMPARATOR);
    set.add(note);
    return set;
  }

  private Pair<RbelElement, RbelMismatchNoteFacet> buildMismatchPair(
      RbelMismatchNoteFacet.MismatchType type, long sequence, String message) {
    RbelElement root = new RbelElement();
    root.setSequenceNumber(sequence);
    root.addFacet(
        RbelTcpIpMessageFacet.builder()
            .sender(new RbelElement())
            .receiver(new RbelElement())
            .receivedFromRemoteWithUrl(null)
            .build());
    return Pair.of(root, new RbelMismatchNoteFacet(type, message, root));
  }

  private TestStep buildStep(int line, int column) {
    return TestStepAdapter.builder().line(line).column(column).build();
  }

  private TestCase simpleTestCase(TestStep singleStep) {
    return new BasicTestCase(List.of(singleStep));
  }
}
