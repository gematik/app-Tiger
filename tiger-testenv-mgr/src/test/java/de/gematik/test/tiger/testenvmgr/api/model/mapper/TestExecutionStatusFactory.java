/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.TigerSummaryListener;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.val;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class TestExecutionStatusFactory {
  public static final UUID TEST_UUID = UUID.fromString("39c3f267-10ab-4f1b-a5a4-b56c979bf772");

  public static ScenarioRunner.TestExecutionStatus createTestExecutionStatus() {

    var testDescriptors = createTestDescriptorsWithParents();
    TestPlan testPlan = TestPlan.from(testDescriptors, mock(ConfigurationParameters.class));

    return new ScenarioRunner.TestExecutionStatus(
        TEST_UUID, testPlan, createTigerSummaryListener(testPlan, testDescriptors));
  }

  public static List<TestDescriptor> createTestDescriptorsWithParents() {
    List<TestDescriptor> testDescriptors = new ArrayList<>();
    testDescriptors.add(new DummyTestDescriptor("dummyTest1"));
    testDescriptors.add(new DummyTestDescriptor("dummyTest2"));

    List<TestDescriptor> testDescriptorsInContainer =
        List.of(new DummyTestDescriptor("dummyTest3"), new DummyTestDescriptor("dummyTest4"));

    TestDescriptor container = new DummyTestContainerDescriptor(testDescriptorsInContainer);
    testDescriptors.add(container);
    return testDescriptors;
  }

  public static List<TestDescriptor> createTestDescriptorsFlat() {
    return List.of(
        new DummyTestDescriptor("dummyTest1"),
        new DummyTestDescriptor("dummyTest2"),
        new DummyTestDescriptor("dummyTest3"),
        new DummyTestDescriptor("dummyTest4"));
  }

  private static TigerSummaryListener createTigerSummaryListener(
      TestPlan testPlan, List<TestDescriptor> testDescriptors) {
    val summaryListener = new TigerSummaryListener(testPlan);

    // the call for testPlanExecution ensures the TigerSummaryListener is correctly initialized
    summaryListener.testPlanExecutionStarted(testPlan);

    var successful = List.of(testDescriptors.get(0), testDescriptors.get(1));

    var children = testDescriptors.get(2).getChildren().stream().toList();
    var aborted = children.get(0);
    var failed = children.get(1);

    successful.forEach(
        testDescriptor ->
            summaryListener.executionFinished(
                TestIdentifier.from(testDescriptor), TestExecutionResult.successful()));
    summaryListener.executionFinished(
        TestIdentifier.from(aborted),
        TestExecutionResult.aborted(new AssertionError("test aborted")));
    summaryListener.executionFinished(
        TestIdentifier.from(failed), TestExecutionResult.failed(new AssertionError("test failed")));
    summaryListener.testPlanExecutionFinished(testPlan);
    return summaryListener;
  }
}
