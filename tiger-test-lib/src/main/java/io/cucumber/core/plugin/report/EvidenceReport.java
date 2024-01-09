/*
 * Copyright (c) 2024 gematik GmbH
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

package io.cucumber.core.plugin.report;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
public class EvidenceReport {

  @Value
  @AllArgsConstructor
  public static class Step {

    String name;

    public Step(String name) {
      this.name = name;
      evidenceEntries = Collections.unmodifiableList(new ArrayList<>());
    }

    @With(AccessLevel.PRIVATE)
    List<Evidence> evidenceEntries;

    public Step addEntry(Evidence stepEvidenceToAdd) {
      return withEvidenceEntries(
          Stream.concat(evidenceEntries.stream(), Stream.of(stepEvidenceToAdd)).toList());
    }
  }

  @Value
  public static class ReportContext {

    String scenario;
    URI feature;
  }

  ReportContext context;

  List<Step> steps;
}
