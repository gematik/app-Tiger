/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyJexlCriterionRoutes extends AbstractTigerProxyTest {

  @Test
  void competingRoutes_shouldSelectSecondRoute() {
    final String wrongDst = "http://localhost:" + fakeBackendServerPort + "/wrong";
    final String correctDst = "http://localhost:" + fakeBackendServerPort + "/right";

    spawnTigerProxyWithDefaultRoutesAndWith(TigerProxyConfiguration.builder().build());

    final BiConsumer<TigerRoute, TigerRoute> testRoutesInOrder =
        (route1, route2) -> {
          tigerProxy.clearAllRoutes();
          tigerProxy.addRoute(route1);
          tigerProxy.addRoute(route2);

          proxyRest
              .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar/blub.html")
              .asString();
          awaitMessagesInTiger(2);

          assertThat(tigerProxy.getRbelMessages())
              .last()
              // get the last request
              .extracting(
                  response -> response.getFacetOrFail(RbelHttpResponseFacet.class).getRequest())
              // get the request url
              .extracting(
                  request ->
                      request
                          .getFacetOrFail(RbelHttpRequestFacet.class)
                          .getPath()
                          .getRawStringContent())
              // do the assertions
              .matches(path -> !path.contains("wrong"), "Does the path not contain 'wrong'?")
              .matches(path -> path.contains("right"), "Does the path contain 'right'?");
        };

    testRoutesInOrder.accept(route("/foobar/", correctDst), route("/foo/", wrongDst));
    testRoutesInOrder.accept(route("/foo/", wrongDst), route("/foobar/", correctDst));
    testRoutesInOrder.accept(route("/foo/", wrongDst), route("/foobar", correctDst));
  }

  @Test
  void testRouteWithJexlCriterion() {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("/foobar/")
            .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar/")
            .criterions(List.of("$.header.foo != 'bar'"))
            .build());
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("/foobar/")
            .to("http://localhost:" + fakeBackendServerPort + "/foobar/")
            .criterions(List.of("$.header.foo == 'bar'"))
            .build());

    assertThat(
            proxyRest
                .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar/blub.html")
                .asString()
                .getStatus())
        .isEqualTo(777);
    assertThat(
            proxyRest
                .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar/blub.nohtml")
                .header("foo", "bar")
                .asString()
                .getStatus())
        .isEqualTo(666);
  }

  private TigerRoute route(String from, String to) {
    return TigerRoute.builder().from(from).to(to).build();
  }
}
