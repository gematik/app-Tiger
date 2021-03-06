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

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.TriConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
public class TestTigerProxyCompetingRoutes extends AbstractTigerProxyTest {

    @Test
    public void competingRoutes_shouldSelectSecondRoute() {
        final String wrongDst = "http://localhost:" + fakeBackendServer.port() + "/wrong";
        final String correctDst = "http://localhost:" + fakeBackendServer.port() + "/right";

        TigerGlobalConfiguration.reset();
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .build());

        final TriConsumer<TigerRoute, TigerRoute, String> testRoutesInOrder = (route1, route2, targetPath) -> {
            tigerProxy.clearAllRoutes();
            tigerProxy.addRoute(route1);
            tigerProxy.addRoute(route2);

            proxyRest.get("http://localhost:" + tigerProxy.getProxyPort() + targetPath)
                .asString();

            assertThat(tigerProxy.getRbelMessages()).last()
                // get the last request
                .extracting(response -> response.getFacetOrFail(RbelHttpResponseFacet.class).getRequest())
                // get the request url
                .extracting(
                    request -> request.getFacetOrFail(RbelHttpRequestFacet.class).getPath().getRawStringContent())

                .matches(path -> !path.contains("wrong"), "Does the path not contain 'wrong'?")
                .matches(path -> path.contains("right"), "Does the path contain 'right'?");
        };

        testRoutesInOrder.accept(route("/foobar/", correctDst), route("/foo/", wrongDst), "/foobar/blub.html");
        testRoutesInOrder.accept(route("/foo/", wrongDst), route("/foobar/", correctDst), "/foobar/blub.html");
        testRoutesInOrder.accept(route("/foo/", wrongDst), route("/foobar", correctDst), "/foobar/blub.html");
    }

    private TigerRoute route(String from, String to) {
        return TigerRoute.builder()
            .from(from)
            .to(to)
            .build();
    }

}
