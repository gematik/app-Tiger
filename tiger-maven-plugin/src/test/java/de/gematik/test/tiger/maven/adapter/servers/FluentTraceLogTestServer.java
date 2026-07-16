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
package de.gematik.test.tiger.maven.adapter.servers;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;

@TigerServerType("fluentTraceLogTestServer")
public class FluentTraceLogTestServer extends AbstractTigerServer {

  public FluentTraceLogTestServer(
      String serverId, CfgServer configuration, TigerTestEnvMgr envMgr) {
    super(serverId, configuration, envMgr);
  }

  @Override
  public void performStartup() {
    log.atTrace().log("fluent trace log from test server startup");
  }

  @Override
  public void shutdown() {
    // no-op
  }
}
