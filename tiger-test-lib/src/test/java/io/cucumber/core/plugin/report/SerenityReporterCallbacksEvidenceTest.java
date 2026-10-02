/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package io.cucumber.core.plugin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.core.reports.AndContent;
import net.serenitybdd.core.reports.WithTitle;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SerenityReporterCallbacksEvidenceTest {

  private final EvidenceRecorder recorder = EvidenceRecorderFactory.getEvidenceRecorder();
  private final SerenityReporterCallbacks callbacks = new SerenityReporterCallbacks();

  @BeforeEach
  void setUp() {
    recorder.reset();
    recorder.openStepContext(new ReportStepConfiguration("FHIRPath assertion"));
  }

  @AfterEach
  void tearDown() {
    recorder.reset();
  }

  @Test
  void recordsCollectionElementsWithoutInspectingTheirGetters() {
    // FHIRPath returns an ArrayList of model objects. On Java 21, treating that list as a
    // bean visits getFirst()/getLast(), then model getters that auto-create more elements.
    var details = new ArrayList<>(List.of(new AutoCreatingElement()));

    String contents = recordEvidence(details);

    assertThat(new JSONArray(contents).toList()).containsExactly("true");
  }

  @Test
  void recordsTextEvidenceAsText() {
    assertThat(recordEvidence("validation details")).isEqualTo("validation details");
  }

  @Test
  void recordsPrimitiveArrayEvidence() {
    assertThat(new JSONArray(recordEvidence(new int[] {1, 2})).toList()).containsExactly(1, 2);
  }

  @Test
  void recordsEvidenceWithoutDetails() {
    assertThat(recordEvidence(null)).isNull();
  }

  private String recordEvidence(Object details) {
    recorder.recordEvidence(new Evidence(Evidence.Type.INFO, "Result", details));
    var title = mock(WithTitle.class, RETURNS_SELF);
    var content = mock(AndContent.class);
    when(title.withTitle(anyString())).thenReturn(content);

    try (var serenity = mockStatic(Serenity.class)) {
      serenity.when(Serenity::recordReportData).thenReturn(title);

      ReflectionTestUtils.invokeMethod(callbacks, "addStepEvidence");

      verify(title).asEvidence();
      verify(title).withTitle("INFO - Result");
      var captured = ArgumentCaptor.forClass(String.class);
      verify(content).andContents(captured.capture());
      return captured.getValue();
    }
  }

  public static class AutoCreatingElement {
    public AutoCreatingElement getIdElement() {
      return new AutoCreatingElement();
    }

    @Override
    public String toString() {
      return "true";
    }
  }
}
