/*
 * Copyright (c) 2023 gematik GmbH
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
class TigerProxyMeshTest extends AbstractTestTigerTestEnvMgr {

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
    @Disabled("deactivated due to buildserver problems") // TODO TGR-794
    public void aggregateFromOneRemoteProxy(TigerTestEnvMgr envMgr) {
        final String path = "/foobarschmar";
        Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.5") + path)
            .asString()
            .getStatus();

        await().atMost(10, TimeUnit.SECONDS)
            .until(() ->
                envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 2
                    && envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().getFirst()
                    .findElement("$.path").map(RbelElement::getRawStringContent)
                    .map(p -> p.endsWith(path))
                    .orElse(false));
    }

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
        + "    dependsUpon: reverseProxy1, reverseProxy2\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.2}\n"
        + "      proxyPort: ${free.port.3}\n"
        + "      activateRbelParsing: false\n"
        + "      rbelBufferSizeInMb: 0\n"
        + "      trafficEndpoints:\n"
        + "        - http://localhost:${free.port.4}\n"
        + "        - http://localhost:${free.port.6}\n"
        + "  reverseProxy1:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.4}\n"
        + "      proxiedServer: winstone\n"
        + "      proxyPort: ${free.port.5}\n"
        + "  reverseProxy2:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.6}\n"
        + "      proxiedServer: winstone\n"
        + "      proxyPort: ${free.port.7}\n")
    @Disabled("deactivated due to buildserver problems") // TODO TGR-794
    public void testWithMultipleUpstreamProxies(TigerTestEnvMgr envMgr) {
        assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory())
            .isEmpty();

        assertThat(Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.5"))
            .asString()
            .getStatus())
            .isEqualTo(200);
        assertThat(Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.7"))
            .asString()
            .getStatus())
            .isEqualTo(200);

        await().atMost(10, TimeUnit.SECONDS)
            .until(() ->
                envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 4);
    }

    @SneakyThrows
    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.2}\n"
        + "servers:\n"
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
        + "      proxyPort: ${free.port.5}\n"
        + "      directReverseProxy:\n"
        + "        hostname: localhost\n"
        + "        port: ${free.port.6}\n")
    @Disabled("deactivated due to buildserver problems") // TODO TGR-794
    void testDirectReverseProxyMeshSetup_withoutResponse(TigerTestEnvMgr envMgr) {
        try (Socket clientSocket = new Socket("localhost",
            TigerGlobalConfiguration.readIntegerOptional("free.port.5").get());
            ServerSocket serverSocket = new ServerSocket(TigerGlobalConfiguration.readIntegerOptional("free.port.6").get())) {

            clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
            clientSocket.getOutputStream().flush();
            serverSocket.accept();

            await().atMost(10, TimeUnit.SECONDS)
                .until(() ->
                    envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 1);
        }
    }

    @SneakyThrows
    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.2}\n"
        + "servers:\n"
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
        + "      proxyPort: ${free.port.5}\n"
        + "      directReverseProxy:\n"
        + "        hostname: localhost\n"
        + "        port: ${free.port.2}\n")
    void testDirectReverseProxyMeshSetup_withResponse(TigerTestEnvMgr envMgr) {
        try (Socket clientSocket = new Socket("127.0.0.1",
            Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.5}")))) {

            clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
            clientSocket.getOutputStream().flush();
            await().atMost(10, TimeUnit.SECONDS)
                .until(() ->
                    envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 1);
        }
    }
}
