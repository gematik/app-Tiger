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
package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.AbstractTestTigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class TigerProxyServerTest extends AbstractTestTigerTestEnvMgr {

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
              someTigerProxy:
                  type: tigerProxy
                  hostname: someOtherProxy
                  tigerProxyConfiguration:
                    proxyRoutes:
                      - from: /
                        to: http://localhost:${free.port.1}
                    proxyPort: ${free.port.0}
                    adminPort: ${free.port.1}
          """)
  void testCreateHttpBinWithRandomPort(TigerTestEnvMgr envMgr) {
    final UnirestInstance unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().verifySsl(true);
    unirestInstance
        .config()
        .sslContext(
            ((TigerProxyServer) envMgr.getServers().get("someTigerProxy"))
                .getTigerProxy()
                .buildSslContext());

    unirestInstance
        .config()
        .proxy(
            "localhost", TigerGlobalConfiguration.readIntegerOptional("free.port.0").orElseThrow());
    unirestInstance.get("https://someTigerProxy/status/200").asString();
    unirestInstance.get("https://someOtherProxy/status/200").asString();
  }
}
