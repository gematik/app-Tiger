/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.concurrent.TimeUnit;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
public class TigerProxyMeshTest extends AbstractTestTigerTestEnvMgr {

    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.2}\n"
        + "\n"
        + "servers:\n"
        + "  winstone:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:target/winstone.jar\n"
        + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n"
        + "  aggregatingProxy:\n"
        + "    type: tigerProxy\n"
        + "    dependsUpon: reverseProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.2}\n"
        + "      proxyPort: ${free.port.3}\n"
        + "      activateRbelParsing: false\n"
        + "      rbelBufferSizeInMb: 0\n"
        + "      trafficEndpoints:\n"
        + "        - http://localhost:${free.port.4}\n"
        + "  reverseProxy:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.4}\n"
        + "      proxiedServer: winstone\n"
        + "      proxyPort: ${free.port.5}\n")
    public void testCreateExternalJarRelativePathWithWorkingDir(TigerTestEnvMgr envMgr) {
        final String path = "/foobarschmar";
        Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.5") + path)
            .asString()
            .getStatus();

        await().atMost(10, TimeUnit.SECONDS)
            .until(() ->
                envMgr.getLocalTigerProxy().getRbelLogger().getMessageHistory().size() >= 2
                    && envMgr.getLocalTigerProxy().getRbelLogger().getMessageHistory().get(0)
                    .findElement("$.path").map(RbelElement::getRawStringContent)
                    .map(p -> p.endsWith(path))
                    .orElse(false));
    }
}
