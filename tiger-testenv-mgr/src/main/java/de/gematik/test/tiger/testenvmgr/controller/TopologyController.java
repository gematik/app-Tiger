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
package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.topology.ConfigurationDiagramModel;
import de.gematik.test.tiger.topology.LiveDiagramBuilderKt;
import de.gematik.test.tiger.topology.RemoteProxyConfigurationReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/topology")
@Slf4j
public class TopologyController {

  private final RemoteProxyConfigurationReader remoteProxyConfigurationReader =
      new RemoteProxyConfigurationReader();

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ConfigurationDiagramModel getTopology() {
    log.trace("Fetch request for live topology diagram");
    return LiveDiagramBuilderKt.buildDiagramFromLiveConfiguration(remoteProxyConfigurationReader);
  }
}
