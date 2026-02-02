/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
                    TigerConfigurationRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.User-Agent")
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
                        .condition("isResponse")
                        .targetElement("$.body")
                        .replaceWith(jsonBody)
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse && request.url =$ 'foobar'")
                        .targetElement("$.header.Some-Header-Field")
                        .replaceWith("modified value")
                        .build()))
            .build());

    proxyRest.post("http://backend/notFoobar").asJson();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.User-Agent")
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
  void keepOrderOfModifications() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
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
                        .targetElement("$.header.User-Agent")
                        .replaceWith("modified user-agent")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.User-Agent")
                        .replaceWith("modified user-agent 2")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse && request.url =$ 'foobar'")
                        .targetElement("$.header.Some-Header-Field")
                        .replaceWith("modified value")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse && request.url =$ 'foobar'")
                        .targetElement("$.header.Some-Header-Field")
                        .replaceWith("modified value 2")
                        .build()))
            .build());

    proxyRest.post("http://backend/notFoobar").asJson();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.User-Agent")
        .hasStringContentEqualTo("modified user-agent 2");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.Some-Header-Field")
        .hasStringContentEqualTo("modified value 2");
  }

  @Test
  void replaceStuffForReverseRoute() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.User-Agent")
                        .replaceWith("modified user-agent")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement("$.body")
                        .replaceWith("{\"another\":{\"node\":{\"path\":\"correctValue\"}}}")
                        .build(),
                    RbelModificationDescription.builder()
                        .condition("isResponse && request.url =$ 'foobar'")
                        .targetElement("$.header.Some-Header-Field")
                        .replaceWith("modified value")
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.header.User-Agent"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("modified user-agent");
    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.another.node.path"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("correctValue");
    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.header.Some-Header-Field")
        .hasStringContentEqualTo("modified value");
  }

  @Test
  void regexModifications() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
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
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.foo"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("boo");
  }

  @Test
  void regexModificationRemovesPoppHeaderLine() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header")
                        .regexFilter("(?m)^popp:\\s*[^\\n]*\\n?")
                        .replaceWith("")
                        .build()))
            .build());

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .header("abc", "dummy-value")
        .header("popp", "dummy-token")
        .header("zyx", "value")
        .asString();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .doesNotHaveChildWithPath("$.header.popp");
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .hasChildWithPath("$.header.abc");
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .hasChildWithPath("$.header.zyx");
  }

  @Test
  void noModificationsForwardProxy_shouldLeaveBinaryContentUntouched() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    final byte[] body =
        proxyRest.post("http://backend/foobar").body(binaryMessageContent).asBytes().getBody();
    awaitMessagesInTigerProxy(2);

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
    awaitMessagesInTigerProxy(2);

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
    String specialCaseParameter = "blub" + RandomStringUtils.insecure().nextAlphanumeric(300);
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
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
                    TigerConfigurationRoute.builder()
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
                    TigerConfigurationRoute.builder()
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

  @SneakyThrows
  @Test
  void modifyReasonPhrase_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
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

    try (var apacheClient = HttpClients.createDefault()) {
      CloseableHttpResponse response =
          apacheClient.execute(
              RequestBuilder.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
                  .build());

      assertThat(response.getStatusLine().getReasonPhrase()).isEqualTo("Foo bar Bar Foobar");
    }
  }

  @Test
  void removeReasonPhrase_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
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

  @ParameterizedTest
  @CsvSource({
    "$.header.my-new-header, my-new-header",
    "$.header.['X-this-another-header'], X-this-another-header",
  })
  void modificationWithAddingHeader_shouldWork(String newHeaderTarget, String newHeaderName) {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isResponse")
                        .targetElement(newHeaderTarget)
                        .replaceWith("my new header value")
                        .build()))
            .build());
    HttpResponse<JsonNode> response =
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
    assertThat(response.getHeaders().get(newHeaderName).get(0)).isEqualTo("my new header value");
  }

  @Test
  void addingHeaderWithContent_shouldWork() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("$.method =^ 'POST'")
                        .targetElement("$.header.foobar")
                        .replaceWith("my new header value")
                        .build()))
            .build());
    final String body = "{\"someKey\": \"some Value\"}";
    HttpResponse<String> response =
        Unirest.post("http://localhost:" + tigerProxy.getProxyPort() + "/echo")
            .body(body)
            .asString();
    assertThat(response.getBody()).isEqualTo(body);
  }

  @Test
  void selfReferentialHeaderAddedModification() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.extra-foo")
                        .replaceWith("?{$.header.[~'foo']}-modified")
                        .build()))
            .build());
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/echo")
        .header("FOO", "bar")
        .asString();
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.extra-foo")
        .hasStringContentEqualTo("bar-modified");
  }

  @Test
  void defunctModification_shouldStillTrasmit() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("thisDoesNot('exist')")
                        .targetElement("$.header.user-agent")
                        .replaceWith("Something")
                        .build()))
            .build());
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/echo").asString();
    assertThat(tigerProxy.getRbelMessagesList()).hasSize(2);
  }

  @Test
  void selfReferentialModification() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.user-agent")
                        .replaceWith("?{$.header.[~'foo']}-modified")
                        .build()))
            .build());
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/echo")
        .header("FOO", "bar")
        .asString();
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.[~'user-agent']")
        .hasStringContentEqualTo("bar-modified");
  }

  @Test
  void selfReferentialModificationWithLocalRbelSelector() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .modifications(
                List.of(
                    RbelModificationDescription.builder()
                        .condition("isRequest")
                        .targetElement("$.header.json-header")
                        .replaceWith("?{$.foo}-modified")
                        .build()))
            .build());
    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/echo")
        .header("json-header", "{'foo':'bar'}")
        .asString();
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.header.[~'json-header']")
        .hasStringContentEqualTo("bar-modified");
  }
}
