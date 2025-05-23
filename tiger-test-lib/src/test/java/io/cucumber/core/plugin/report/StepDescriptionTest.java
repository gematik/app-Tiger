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
package io.cucumber.core.plugin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.glue.ResolvableArgument;
import io.cucumber.plugin.event.DataTableArgument;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Step;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class just tests (as of now 2025-02) if a method is resolvable via the BeanUtils, including
 * method signatures that contain generics info on the params. If you want to extend this test class
 * to also check for actual resolution of parameters you will have to extend the mocking.
 */
class StepDescriptionTest {

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Mock private PickleStepTestStep pickleStepTestStep;
  @Mock private Step step;
  @Mock private DataTableArgument dataTableArgument;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethod(java.util.List<java.util.Map<String,"
            + " Integer>>)",
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethod2(java.lang.String)",
      })
  void isMethodResolvable_OK(String methodName) {
    when(pickleStepTestStep.getStep()).thenReturn(step);
    when(step.getKeyword()).thenReturn("When");
    when(step.getText()).thenReturn(" test resolving methods ${tiger.tigerGlue.helloTestLocal}");
    when(step.getArgument()).thenReturn(dataTableArgument);
    when(dataTableArgument.cells()).thenReturn(List.of(List.of("1", "2", "3")));
    when(pickleStepTestStep.getCodeLocation()).thenReturn(methodName);
    assertThat(StepDescription.of(pickleStepTestStep).isMethodResolvable(methodName)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethodNOT(java.util.List<java.util.Map<String,"
            + " Integer>>)",
        // unknown method
        "io.cucumber.core.plugin.report.StepDescriptionTestNOT.testMethod(java.util.List<java.util.Map<String,"
            + " Integer>>)",
        // unknown class
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethod1(java.util.Map)",
        // wrong params
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethod1(java.util.Map2)",
        // unknown param type
        "io.cucumber.core.plugin.report.StepDescriptionTest.testMethod3()"
        // no resolvable annotation
      })
  void isMethodResolvable_NOK(String methodName) {
    when(pickleStepTestStep.getStep()).thenReturn(step);
    when(step.getKeyword()).thenReturn("When");
    when(step.getText()).thenReturn(" test resolving methods");
    when(step.getArgument()).thenReturn(dataTableArgument);
    when(dataTableArgument.cells()).thenReturn(List.of(List.of("1", "2", "3")));
    when(pickleStepTestStep.getCodeLocation()).thenReturn(methodName);
    assertThat(StepDescription.of(pickleStepTestStep).isMethodResolvable(methodName)).isFalse();
  }

  @ResolvableArgument
  void testMethod(List arg1) {}

  @ResolvableArgument
  List testMethod2(String arg1) {
    return null;
  }

  List testMethod3(String arg1) {
    return null;
  }
}
