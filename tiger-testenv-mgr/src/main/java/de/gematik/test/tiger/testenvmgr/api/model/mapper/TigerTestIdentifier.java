/*
 *
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
 */
package de.gematik.test.tiger.testenvmgr.api.model.mapper;

import de.gematik.test.tiger.testenvmgr.util.ScenarioCollector;
import java.util.Arrays;
import lombok.Data;
import org.junit.platform.launcher.TestIdentifier;

@Data
public class TigerTestIdentifier {

  private final TestIdentifier testIdentifier;
  private final ScenarioCollector.TestDescription testDescription;

  public TigerTestIdentifier(
      TestIdentifier testIdentifier, ScenarioCollector.TestDescription testDescription) {
    this.testIdentifier = testIdentifier;
    this.testDescription = testDescription;
  }

  public TigerTestIdentifier(TestIdentifier testIdentifier, String descriptionAsString) {
    this.testIdentifier = testIdentifier;
    this.testDescription =
        new ScenarioCollector.TestDescription(Arrays.asList(descriptionAsString.split(":")));
  }

  public String getDisplayName() {
    return testDescription.fullDescription();
  }

  public String getFeatureName() {
    return testDescription.getFeatureName();
  }

  public String getScenarioName() {
    return testDescription.getScenarioName();
  }
}
