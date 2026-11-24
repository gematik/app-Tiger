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
package com.example.tiger;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;

@TigerServerType("customerServer")
public class CustomerServer extends CustomerServerBase {
  public CustomerServer(String serverId, CfgServer configuration, TigerTestEnvMgr environment) {
    super(serverId, configuration, environment);
  }

  @Override
  public void performStartup() {
    // Discovery fixture only; no external server needs to be started.
  }

  @Override
  public void shutdown() {
    // This fixture starts no external resources, so there is nothing to shut down.
  }
}

@TigerServerType("abstractCustomerServer")
abstract class CustomerServerBase extends AbstractTigerServer {
  protected CustomerServerBase(
      String serverId, CfgServer configuration, TigerTestEnvMgr environment) {
    super(serverId, configuration, environment);
  }
}
