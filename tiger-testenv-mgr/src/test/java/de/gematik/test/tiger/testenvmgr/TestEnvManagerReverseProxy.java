/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
public class TestEnvManagerReverseProxy extends AbstractTestTigerTestEnvMgr {

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testWinstone2:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - http://localhost:${mockserver.port}/download\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n"
        + "      healthcheck: http://127.0.0.1:${free.port.0}\n"
        + "  reverseproxy1:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.2}\n"
        + "      proxiedServer: testWinstone2\n"
        + "      proxyPort: ${free.port.3}\n")
    public void testReverseProxy() {
        final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
            "http://127.0.0.1:" + TigerGlobalConfiguration.readStringOptional("free.port.3").get()).asString();
        assertThat(httpResponse.getBody().trim())
            .withFailMessage("Expected to receive folder index page from Winstone server, but got HTTP " +
                httpResponse.getStatus() + " with body \n" + httpResponse.getBody())
            .startsWith("<HTML>").endsWith("</HTML>");
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml")
    public void testReverseProxyManual() {
        log.info("Entering test");
        final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
            "http://127.0.0.1:" + TigerGlobalConfiguration.readStringOptional("free.port.2").get()).asString();
        assertThat(httpResponse.getBody().trim())
            .withFailMessage("Expected to receive folder index page from Winstone server, but got HTTP " +
                httpResponse.getStatus() + " with body \n" + httpResponse.getBody())
            .startsWith("<HTML>").endsWith("</HTML>")
            .contains("<H1>Directory:");
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testWinstone2:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - http://localhost:${mockserver.port}/download\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=..\n"
        + "      healthcheck: http://127.0.0.1:${free.port.0}/foo/bar/stuff\n"
        + "  reverseproxy1:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.2}\n"
        + "      proxiedServer: testWinstone2\n"
        + "      proxyPort: ${free.port.3}\n")
    public void deepPathHealthcheckUrl_routeShouldTargetBaseUrl() {
        final kong.unirest.HttpResponse<String> httpResponse = Unirest.get(
            "http://127.0.0.1:" + TigerGlobalConfiguration.readStringOptional("free.port.3").get()).asString();
        assertThat(httpResponse.getBody())
            .contains("<TITLE>Directory: /</TITLE>");
    }
}
