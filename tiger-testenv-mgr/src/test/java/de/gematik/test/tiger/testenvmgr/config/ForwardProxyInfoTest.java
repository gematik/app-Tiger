/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class ForwardProxyInfoTest {

    @SneakyThrows
    @Test
    @TigerTest(tigerYaml = """
        tigerProxy:
          forwardToProxy:
            hostname: 127.0.0.1
            port: ${free.port.20}
        servers:
          someProxyServer:
            type: tigerProxy
            tigerProxyCfg:
              adminPort: ${free.port.10}
              proxyPort: ${free.port.20}
          virtualExternalServer:
            type: externalUrl
            dependsUpon: someProxyServer
            source:
              - http://localhost:${free.port.10}/foobar
        """)
    void localSource_shouldNotUseForwardProxy(TigerTestEnvMgr envMgr) {
        assertThat(((TigerProxyServer) envMgr.getServers().get("someProxyServer"))
                    .getApplicationContext()
                    .getBean(TigerProxy.class)
                    .getRbelMessages())
            .isEmpty();
    }

    @SneakyThrows
    @Test
    @TigerTest(tigerYaml = """
        tigerProxy:
          forwardToProxy:
            hostname: 127.0.0.1
            port: ${free.port.20}
        servers:
          someProxyServer:
            type: tigerProxy
            tigerProxyCfg:
              adminPort: ${free.port.10}
              proxyPort: ${free.port.20}
          virtualExternalServer:
            type: externalUrl
            dependsUpon: someProxyServer
            source:
              - http://google.com/foobar
        """)
    void externalSource_shouldUseForwardProxy(TigerTestEnvMgr envMgr) {
        await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() ->
                ((TigerProxyServer) envMgr.getServers().get("someProxyServer"))
                    .getApplicationContext()
                    .getBean(TigerProxy.class)
                    .getRbelMessages().stream()
                    .anyMatch(el -> el.getFacet(RbelHttpRequestFacet.class)
                        .map(req -> req.getPath().getRawStringContent().contains("foobar"))
                        .orElse(false)));
    }
}
