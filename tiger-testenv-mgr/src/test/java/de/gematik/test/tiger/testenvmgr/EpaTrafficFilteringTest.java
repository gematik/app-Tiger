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
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
class EpaTrafficFilteringTest extends AbstractTestTigerTestEnvMgr {

    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n"
        + "  skipTrafficEndpointsSubscription: true\n"
        + "  activateVauAnalysis: true\n"
        + "  trafficEndpointFilterString: \"$.body.recordId == 'X114428539'\"\n"
        + "  keyFolders:\n"
        + "    - '../tiger-proxy/src/test/resources'\n"
        + "  trafficEndpoints:\n"
        + "    - http://localhost:${free.port.1}\n"
        + "servers:\n"
        + "  upstreamProxy:\n"
        + "    type: tigerProxy\n"
        + "    tigerProxyCfg:\n"
        + "      adminPort: ${free.port.1}\n"
        + "      proxyPort: ${free.port.2}\n")
    void filterForEpaKvnr(TigerTestEnvMgr envMgr) {
        final TigerProxy upstreamTigerProxy = ((TigerProxyServer) envMgr.getServers().get("upstreamProxy"))
            .getTigerProxy();
        final RbelConverter upstreamRbelConverter = upstreamTigerProxy.getRbelLogger().getRbelConverter();

        upstreamRbelConverter.addPostConversionListener((el, conv) -> upstreamTigerProxy.triggerListener(el));
        RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/vauEpa2Flow.tgr")
            .rbelConverter(upstreamRbelConverter)
            .build().initialize();

        await()
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> envMgr.getLocalTigerProxy().getRbelMessages().getFirst().findElement("$.body.recordId")
                .get()
                .getFacetOrFail(RbelValueFacet.class)
                .getValue().equals("X114428539"));
    }
}
