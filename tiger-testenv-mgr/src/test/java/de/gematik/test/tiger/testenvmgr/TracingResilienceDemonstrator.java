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

import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * This file is deliberately named "..Tests" to NOT execute it during the build. The tests here are to
 * stress-test a more complicated mesh-setup. It should be converted to a fully-fleshed unit-test eventually.
 */
@Slf4j
public class TracingResilienceDemonstrator {

    @Test
    @TigerTest(tigerYaml =
        "tigerProxy:\n"
            + "  adminPort: 8082\n"
            + "  proxyPort: 9092\n"
            + "\n"
            + "servers:\n"
            + "  receivingTigerProxy:\n"
            + "    type: tigerProxy\n"
            + "    tigerProxyCfg:\n"
            + "      trafficEndpoints:\n"
            // Hier wird einfach nur ein tigerProxy referenziert, der auf einem anderen rechner läuft.
            // In meinem lokalen setup war das ein Raspi mit der aktuellen standalone-version.
            + "        - http://192.168.0.40:8081\n"
            + "      adminPort: 8080\n"
            + "      proxyPort: 9090  \n"
            + "      downloadInitialTrafficFromEndpoints: true\n"
            + "      connectionTimeoutInSeconds: 100")
    public void generateTrafficAndBounceViaRemoteProxy(TigerTestEnvMgr testEnvMgr) throws InterruptedException {
        final UnirestInstance instance = Unirest.spawnInstance();
        instance.config().proxy("127.0.0.1", 9092);
        instance.config().followRedirects(false);

        for (int i = 0; i < 10_000; i++) {
            Thread.sleep(10);
            for (int j = 0; j < 10; j++) {
                instance.get("http://google.de").asEmpty();
            }
            log.info("Sent {} msgs, sending-proxy has {} msgs, receiving-proxy has {} msgs",
                (i + 1) * 10 * 2,
                testEnvMgr.getLocalTigerProxy().getRbelMessages().size(),
                testEnvMgr.findServer("receivingTigerProxy")
                    .map(TigerProxyServer.class::cast)
                    .map(TigerProxyServer::getTigerProxy)
                    .get()
                    .getRbelMessages().size());
        }
    }
}
