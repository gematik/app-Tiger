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
package de.gematik.test.tiger.lib;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
@JsonInclude(Include.NON_NULL)
public class TigerLibConfig {

  @Builder.Default private boolean rbelPathDebugging = false;
  @Builder.Default private boolean rbelAnsiColors = true;
  @Builder.Default private boolean addCurlCommandsForRaCallsToReport = true;
  @Builder.Default public boolean activateWorkflowUi = false;
  @Builder.Default public boolean startBrowser = true;
  @Builder.Default public boolean createRbelHtmlReports = true;
  @Builder.Default public boolean createRbelModificationReports = true;
  @Builder.Default public long pauseExecutionTimeoutSeconds = 18000L;
  @Builder.Default public TigerHttpClientConfig httpClientConfig = new TigerHttpClientConfig();
  @Builder.Default private boolean trafficVisualization = false;
  @Builder.Default public boolean clearEnvironmentStartupTraffic = true;
  @Builder.Default public boolean runTestsOnStart = true;
  @Builder.Default public boolean enableTestManagementRestApi = false;
}
