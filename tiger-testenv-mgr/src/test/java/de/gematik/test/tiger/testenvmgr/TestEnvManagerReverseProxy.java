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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.File;
import java.io.IOException;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.SocketUtils;

@Slf4j
@Getter
public class TestEnvManagerReverseProxy extends AbstractTestTigerTestEnvMgr {
    @Test
    public void testReverseProxy() {
        TigerGlobalConfiguration.readFromYaml(
            "servers:\n"
                + "  testWinstone2:\n"
                + "    type: externalJar\n"
                + "    source:\n"
                + "      - http://localhost:${mockserver.port}/download\n"
                + "    externalJarOptions:\n"
                + "      arguments:\n"
                + "        - --httpPort=${free.ports.0}\n"
                + "        - --webroot=.\n"
                + "      healthcheck: http://127.0.0.1:${free.ports.0}\n"
                + "\n"
                + "  reverseproxy1:\n"
                + "    type: tigerProxy\n"
                + "    tigerProxyCfg:\n"
                + "      serverPort: ${free.ports.2}\n"
                + "      proxiedServer: testWinstone2\n"
                + "      proxyCfg:\n"
                + "        port: ${free.ports.3}\n",
            "tiger");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();

            final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
                "http://127.0.0.1:" + getFreePorts().get(3)).asString();
            assertThat(httpResponse.getBody().trim())
                .withFailMessage("Expected to receive folder index page from Winstone server, but got HTTP " +
                    httpResponse.getStatus() + " with body \n" + httpResponse.getBody())
                .startsWith("<HTML>").endsWith("</HTML>");
        });
    }

    @Test
    public void testReverseProxyManual() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();

            final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
                "http://127.0.0.1:" + getFreePorts().get(2)).asString();
            assertThat(httpResponse.getBody().trim())
                .withFailMessage("Expected to receive folder index page from Winstone server, but got HTTP " +
                    httpResponse.getStatus() + " with body \n" + httpResponse.getBody())
                .startsWith("<HTML>").endsWith("</HTML>")
                .contains("<H1>Directory:");
        });
    }

    @Test
    public void deepPathHealthcheckUrl_routeShouldTargetBaseUrl() {
        TigerGlobalConfiguration.readFromYaml(
            "servers:\n"
                + "  testWinstone2:\n"
                + "    type: externalJar\n"
                + "    source:\n"
                + "      - http://localhost:${mockserver.port}/download\n"
                + "    externalJarOptions:\n"
                + "      arguments:\n"
                + "        - --httpPort=${free.ports.0}\n"
                + "        - --webroot=..\n"
                + "      healthcheck: http://127.0.0.1:${free.ports.0}/foo/bar/stuff"
                + "\n"
                + "  reverseproxy1:\n"
                + "    type: tigerProxy\n"
                + "    tigerProxyCfg:\n"
                + "      serverPort: ${free.ports.2}\n"
                + "      proxiedServer: testWinstone2\n"
                + "      proxyCfg:\n"
                + "        port: ${free.ports.3}\n",
            "tiger");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();

            final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
                "http://127.0.0.1:" + getFreePorts().get(3)).asString();
            assertThat(httpResponse.getBody())
                .contains("<TITLE>Directory: /</TITLE>");
        });
    }
}
