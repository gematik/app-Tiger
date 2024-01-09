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

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.AbstractTestTigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class HttpBinServerTest extends AbstractTestTigerTestEnvMgr {

  @SneakyThrows
  @Test
  @TigerTest(
      tigerYaml =
          """
            servers:
                testHttpBin:
                    type: httpbin
                    serverPort: ${free.port.0}""",
      skipEnvironmentSetup = true)
  void testCreateHttpBinWithRandomPort(TigerTestEnvMgr envMgr) {
    assertThatNoException().isThrownBy(envMgr::setUpEnvironment);

    int serverPort = TigerGlobalConfiguration.readIntegerOptional("free.port.0").orElse(0);

    HttpResponse<String> response =
        Unirest.get("http://localhost:" + serverPort + "/status/200").asString();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
  }
}
