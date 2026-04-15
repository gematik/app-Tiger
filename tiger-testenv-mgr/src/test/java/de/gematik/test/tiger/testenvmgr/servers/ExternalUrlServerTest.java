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

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.testenvmgr.AbstractTestTigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import org.junit.jupiter.api.Test;

class ExternalUrlServerTest extends AbstractTestTigerTestEnvMgr {

  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
            myExternalUrlServer:
              type: externalUrl
              source:
                - https://www.example.com/dingsbums
          """)
  void routeOfExternalUrlServerShallHaveFullPath(TigerTestEnvMgr envMgr) {

      var externalUrlRoute = envMgr.getLocalTigerProxyOrFail().getRoutes()
              .stream().filter(r -> "http://myExternalUrlServer".equals(r.getFrom()))
              .findAny();
      assertThat(externalUrlRoute).isPresent();
      assertThat(externalUrlRoute.get().getFrom()).isEqualTo("http://myExternalUrlServer");
      assertThat(externalUrlRoute.get().getTo()).isEqualTo("https://www.example.com/dingsbums");
  }
}
