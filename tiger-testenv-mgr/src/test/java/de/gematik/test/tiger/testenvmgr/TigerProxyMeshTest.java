/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
@ResetTigerConfiguration
class TigerProxyMeshTest extends AbstractTestTigerTestEnvMgr {

    /**
     * This is a very unfortunate hack: Even after extensive analysis these tests failed on repeated execution on only a handful systems.
     * To get the tests running stable this is the measure we took.
     * Note: The conditions for the error to occur (repeated restarting of a testenv-mgr with the given setup in the SAME JVM) seems
     * extremely rare and should never happen in production. This is a hack, but a carefully deliberated one.
     */
    private void waitAtStart() {
        await().pollDelay(2, TimeUnit.SECONDS).until(() -> true);
    }

    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.2}\n"
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
    void aggregateFromOneRemoteProxy(TigerTestEnvMgr envMgr) {
        waitAtStart();
        final String path = "/foobarschmar";

        HttpResponse<String> response = Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.5") + path).asString();
        int status = response.getStatus();

        assertThat(status).isEqualTo(404);

        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> {
                log.info("Message History: {}", envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size());
                return envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 2
                    && envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().getFirst()
                    .findElement("$.path").map(RbelElement::getRawStringContent)
                    .map(p -> p.endsWith(path))
                    .orElse(false);
            }
            );
    }

    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.12}\n"
        + "\n"
        + "servers:\n"
        + "  winstone:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:target/winstone.jar\n"
        + "    healthcheckUrl: http://127.0.0.1:${free.port.10}\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.10}\n"
        + "        - --webroot=.\n"
        + "  aggregatingProxy:\n"
        + "    type: tigerProxy\n"
        + "    dependsUpon: reverseProxy1, reverseProxy2\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.12}\n"
        + "      proxyPort: ${free.port.13}\n"
        + "      activateRbelParsing: false\n"
        + "      rbelBufferSizeInMb: 0\n"
        + "      trafficEndpoints:\n"
        + "        - http://localhost:${free.port.14}\n"
        + "        - http://localhost:${free.port.16}\n"
        + "  reverseProxy1:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.14}\n"
        + "      proxiedServer: winstone\n"
        + "      proxyPort: ${free.port.15}\n"
        + "  reverseProxy2:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.16}\n"
        + "      proxiedServer: winstone\n"
        + "      proxyPort: ${free.port.17}\n")
    void testWithMultipleUpstreamProxies(TigerTestEnvMgr envMgr) {
        waitAtStart();
        assertThat(envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory())
            .isEmpty();

        assertThat(Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.15"))
            .asString()
            .getStatus())
            .isEqualTo(200);
        assertThat(Unirest.get("http://localhost:" + TigerGlobalConfiguration.readString("free.port.17"))
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
        + "    - http://localhost:${free.port.22}\n"
        + "servers:\n"
        + "  aggregatingProxy:\n"
        + "    type: tigerProxy\n"
        + "    dependsUpon: reverseProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.22}\n"
        + "      proxyPort: ${free.port.23}\n"
        + "      activateRbelParsing: false\n"
        + "      rbelBufferSizeInMb: 0\n"
        + "      trafficEndpoints:\n"
        + "        - http://localhost:${free.port.24}\n"
        + "  reverseProxy:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.24}\n"
        + "      proxyPort: ${free.port.25}\n"
        + "      directReverseProxy:\n"
        + "        hostname: localhost\n"
        + "        port: ${free.port.26}\n")
    void testDirectReverseProxyMeshSetup_withoutResponse(TigerTestEnvMgr envMgr) {
        waitAtStart();
        try (Socket clientSocket = new Socket("localhost",
            TigerGlobalConfiguration.readIntegerOptional("free.port.25").get());
            ServerSocket serverSocket = new ServerSocket(TigerGlobalConfiguration.readIntegerOptional("free.port.26").get())) {
            serverSocket.setSoTimeout(2);
            clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
            clientSocket.getOutputStream().flush();
            try {
                serverSocket.accept();
            } catch (SocketTimeoutException ste) {
                log.warn("socket timeout", ste);
            }
            await().atMost(10, TimeUnit.SECONDS)
                .until(() ->
                    envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 1);
        }
    }

    @SneakyThrows
    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  adminPort: ${free.port.36}\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.32}\n"
        + "servers:\n"
        + "  aggregatingProxy:\n"
        + "    type: tigerProxy\n"
        + "    dependsUpon: reverseProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.32}\n"
        + "      proxyPort: ${free.port.33}\n"
        + "      activateRbelParsing: false\n"
        + "      rbelBufferSizeInMb: 0\n"
        + "      trafficEndpoints:\n"
        + "        - http://localhost:${free.port.34}\n"
        + "  reverseProxy:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.34}\n"
        + "      proxyPort: ${free.port.35}\n"
        + "      directReverseProxy:\n"
        + "        hostname: localhost\n"
        + "        port: ${free.port.36}\n")
    void testDirectReverseProxyMeshSetup_withResponse(TigerTestEnvMgr envMgr) {
        waitAtStart();
        try (Socket clientSocket = new Socket("127.0.0.1",
            Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.35}")))) {
            clientSocket.setSoTimeout(1);
            clientSocket.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
            clientSocket.getOutputStream().flush();
            await().atMost(10, TimeUnit.SECONDS)
                .until(() ->
                    envMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().size() >= 1);
        }
    }
}
