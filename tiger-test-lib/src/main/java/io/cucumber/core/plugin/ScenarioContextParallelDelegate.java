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

import java.net.URI;
import lombok.Getter;
import lombok.experimental.Delegate;

@SuppressWarnings({"java:S1172", "java:S1874"})
public class ScenarioContextParallelDelegate implements IScenarioContext {

  @Getter private final URI featureURI;

  public ScenarioContextParallelDelegate(URI featureURI, ScenarioContextParallel context) {
    this.featureURI = featureURI;
    this.context = context;
  }

  @Delegate private final ScenarioContextParallel context;
}
