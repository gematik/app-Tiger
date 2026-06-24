/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package io.cucumber.core.plugin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseStarted;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureExecutionMonitorTest {

  private FeatureExecutionMonitor underTest;
  private List<URI> completedFeatures;

  @BeforeEach
  void setUp() {
    underTest = new FeatureExecutionMonitor();
    completedFeatures = new ArrayList<>();
    underTest.setOnFeatureCompleted(completedFeatures::add);
    underTest.startTestRun();
  }

  @Test
  @DisplayName("Should not fire callback when first feature starts")
  void shouldNotFireCallbackWhenFirstFeatureStarts() {
    TestCaseStarted event = createTestCaseStartedEvent(URI.create("file:feature1.feature"));

    underTest.startTestCase(event);

    assertThat(completedFeatures).isEmpty();
  }

  @Test
  @DisplayName("Should fire callback when a different feature file starts")
  void shouldFireCallbackWhenDifferentFeatureStarts() {
    URI feature1 = URI.create("file:feature1.feature");
    URI feature2 = URI.create("file:feature2.feature");

    underTest.startTestCase(createTestCaseStartedEvent(feature1));
    underTest.stopTestRun();
    underTest.startTestRun();
    underTest.startTestCase(createTestCaseStartedEvent(feature2));
    underTest.stopTestRun();

    assertThat(completedFeatures).containsExactly(feature1, feature2);
  }

  @Test
  @DisplayName("Should not fire callback when same feature file continues")
  void shouldNotFireCallbackWhenSameFeatureContinues() {
    URI feature1 = URI.create("file:feature1.feature");

    underTest.startTestCase(createTestCaseStartedEvent(feature1));
    underTest.startTestCase(createTestCaseStartedEvent(feature1));

    assertThat(completedFeatures).isEmpty();
  }

  @Test
  @DisplayName("Should fire callbacks for all except last feature transitions in order")
  void shouldFireCallbacksForAllFeatureTransitions() {
    URI feature1 = URI.create("file:feature1.feature");
    URI feature2 = URI.create("file:feature2.feature");
    URI feature3 = URI.create("file:feature3.feature");

    underTest.startTestCase(createTestCaseStartedEvent(feature1));
    underTest.stopTestRun();
    underTest.startTestRun();
    underTest.startTestCase(createTestCaseStartedEvent(feature2));
    underTest.stopTestRun();
    underTest.startTestRun();
    underTest.startTestCase(createTestCaseStartedEvent(feature3));
    underTest.stopTestRun();

    assertThat(completedFeatures).containsExactly(feature1, feature2, feature3);
  }

  @Test
  @DisplayName("Should not fire callback on stopTestRun when no feature was started")
  void shouldNotFireCallbackOnStopWhenNoFeatureStarted() {
    underTest.stopTestRun();

    assertThat(completedFeatures).isEmpty();
  }

  @Test
  @DisplayName("Should handle null callback gracefully")
  void shouldHandleNullCallbackGracefully() {
    underTest.setOnFeatureCompleted(null);
    URI feature1 = URI.create("file:feature1.feature");
    URI feature2 = URI.create("file:feature2.feature");

    assertThatNoException()
        .isThrownBy(
            () -> {
              underTest.startTestCase(createTestCaseStartedEvent(feature1));
              underTest.startTestCase(createTestCaseStartedEvent(feature2));
              underTest.stopTestRun();
            });
  }

  @Test
  @DisplayName("Should handle callback exception gracefully")
  void shouldHandleCallbackExceptionGracefully() {
    underTest.setOnFeatureCompleted(
        uri -> {
          throw new RuntimeException("test exception");
        });

    URI feature1 = URI.create("file:feature1.feature");
    URI feature2 = URI.create("file:feature2.feature");

    assertThatNoException()
        .isThrownBy(
            () -> {
              underTest.startTestCase(createTestCaseStartedEvent(feature1));
              underTest.startTestCase(createTestCaseStartedEvent(feature2));
            });
  }

  private TestCaseStarted createTestCaseStartedEvent(URI featureUri) {
    TestCase testCase = mock(TestCase.class);
    when(testCase.getUri()).thenReturn(featureUri);
    return new TestCaseStarted(Instant.now(), testCase);
  }
}
