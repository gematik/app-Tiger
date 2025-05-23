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

import io.cucumber.plugin.event.TestCase;

public class SerenityUtils {

  private SerenityUtils() {}

  public static String scenarioIdFrom(FeatureFileLoader featureLoader, TestCase testCase) {
    var featureUri = testCase.getUri();
    var node = featureLoader.getAstNode(featureUri, testCase.getLocation().getLine());
    var scenario = TestSourcesModel.getScenarioDefinition(node);
    var feature = featureLoader.getFeature(featureUri);
    return scenarioIdFrom(feature.getName(), TestSourcesModel.convertToId(scenario.getName()));
  }

  private static String scenarioIdFrom(String featureId, String scenarioIdOrExampleId) {
    return (featureId != null && scenarioIdOrExampleId != null)
        ? String.format("%s;%s", featureId, scenarioIdOrExampleId)
        : "";
  }
}
