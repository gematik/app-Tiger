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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyModifications extends AbstractTigerProxyTest {

  @Test
  void replaceStuffForForwardRoute() {
    final String jsonBody = "{\"another\":{\"node\":{\"path\":\"correctValue\"}}}";

    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.user-agent")
                        .replaceWith("modified user-agent")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.path")
                        .replaceWith("/foobar")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.method")
                        .replaceWith("GET")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.method")
                        .replaceWith("GET")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.body")
                        .replaceWith(jsonBody)
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.header.Some-Header-Field")
                        .replaceWith("modified value")
                        .build()))
            .build());

    proxyRest.post("http://backend/notFoobar").asJson();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.user-agent")
        .hasStringContentEqualTo("modified user-agent");
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo("/foobar");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.Some-Header-Field")
        .hasStringContentEqualTo("modified value");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.Content-Length")
        .hasStringContentEqualTo("44");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.body.another.node.path")
        .hasStringContentEqualTo("correctValue");
  }

  @Test
  void replaceStuffForReverseRoute() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.user-agent")
                        .replaceWith("modified user-agent")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.body")
                        .replaceWith("{\"another\":{\"node\":{\"path\":\"correctValue\"}}}")
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.header.user-agent"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("modified user-agent");
    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.another.node.path"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("correctValue");
  }

  @Test
  void regexModifications() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.body")
                        .regexFilter("bar")
                        .replaceWith("boo")
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.foo"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("boo");
  }

  @Test
  void noModificationsForwardProxy_shouldLeaveBinaryContentUntouched() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final byte[] body =
        proxyRest.post("http://backend/foobar").body(binaryMessageContent).asBytes().getBody();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.body"))
        .get()
        .extracting(RbelElement::getRawContent)
        .isEqualTo(binaryMessageContent);
    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body"))
        .get()
        .extracting(RbelElement::getRawContent)
        .isEqualTo(binaryMessageContent);
    assertThat(body).isEqualTo(binaryMessageContent);
  }

  @Test
  void noModificationsReverseProxy_shouldLeaveBinaryContentUntouched() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final byte[] body =
        Unirest.post("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .body(binaryMessageContent)
            .asBytes()
            .getBody();
    awaitMessagesInTiger(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.body"))
        .get()
        .extracting(RbelElement::getRawContent)
        .isEqualTo(binaryMessageContent);
    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body"))
        .get()
        .extracting(RbelElement::getRawContent)
        .isEqualTo(binaryMessageContent);
    assertThat(body).isEqualTo(binaryMessageContent);
  }

  @Test
  void forwardProxyWithModifiedQueryParameters(WireMockRuntimeInfo runtimeInfo) {
    String specialCaseParameter = "blub" + RandomStringUtils.insecure().randomPrint(300);
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .targetElement("$.path.schmoo.value")
                        .replaceWith(specialCaseParameter)
                        .build(),
                    RbelModificationDescription.builder()
                        .targetElement("$.path.[?(content=~'.*bar1')].value")
                        .replaceWith("bar3")
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar?foo=bar1&foo=bar2&schmoo").asString();

    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams())
        .containsOnlyKeys("foo", "schmoo");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("foo").getValues())
        .containsExactly("bar3", "bar2");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("schmoo").getValues())
        .containsExactly(specialCaseParameter);
  }

  @Test
  void reverseProxyWithQueryParameters(WireMockRuntimeInfo runtimeInfo) {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .targetElement("$.path.schmoo.value")
                        .replaceWith("loo")
                        .build(),
                    RbelModificationDescription.builder()
                        .targetElement("$.path.[?(content=~'.*bar1')].value")
                        .replaceWith("bar3")
                        .build()))
            .build());

    Unirest.get(
            "http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=bar1&foo=bar2&schmoo")
        .asString();

    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams())
        .containsOnlyKeys("foo", "schmoo");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("foo").getValues())
        .containsExactly("bar3", "bar2");
    assertThat(getLastRequest(runtimeInfo.getWireMock()).getQueryParams().get("schmoo").getValues())
        .containsExactly("loo");
  }

  @Test
  void modifyStatusCode_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.responseCode")
                        .replaceWith("200")
                        .build()))
            .build());

    HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void modifyReasonPhrase_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.reasonPhrase")
                        .replaceWith("Foo bar Bar Foobar")
                        .build()))
            .build());

    HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

    assertThat(response.getStatusText()).isEqualTo("Foo bar Bar Foobar");
  }

  @Test
  void removeReasonPhrase_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.reasonPhrase")
                        .replaceWith(null)
                        .build()))
            .build());

    HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

    assertThat(response.getStatusText()).isEmpty();
  }
}
