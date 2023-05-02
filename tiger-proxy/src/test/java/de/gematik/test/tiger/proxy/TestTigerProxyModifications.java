/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
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

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
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
                    .build()
            ))
            .build());

        proxyRest.post("http://backend/notFoobar").asJson();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.header.user-agent"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified user-agent");
        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("/foobar");
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.header.Some-Header-Field"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified value");
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.header.Content-Length"))
            .isEmpty(); // not present in mocked response, not added by tiger-proxy
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.another.node.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("correctValue");
    }

    @Test
    void replaceStuffForReverseRoute() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .condition("isRequest")
                    .targetElement("$.header.user-agent")
                    .replaceWith("modified user-agent")
                    .build(),
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.body")
                    .replaceWith("{\"another\":{\"node\":{\"path\":\"correctValue\"}}}")
                    .build()
            ))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.header.user-agent"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified user-agent");
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.another.node.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("correctValue");
    }

    @Test
    void regexModifications() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.body")
                    .regexFilter("bar")
                    .replaceWith("boo")
                    .build()
            ))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body.foo"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("boo");
    }

    @Test
    void noModificationsForwardProxy_shouldLeaveBinaryContentUntouched() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .build());

        final byte[] body = proxyRest.post("http://backend/foobar")
            .body(binaryMessageContent)
            .asBytes().getBody();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(body).isEqualTo(binaryMessageContent);
    }

    @Test
    void noModificationsReverseProxy_shouldLeaveBinaryContentUntouched() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .build());

        final byte[] body = Unirest.post("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .body(binaryMessageContent)
            .asBytes().getBody();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(body).isEqualTo(binaryMessageContent);
    }

    @Test
    void forwardProxyWithModifiedQueryParameters() {
        String specialCaseParameter = "blub" + RandomStringUtils.randomPrint(300);
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .targetElement("$.path.schmoo.value")
                    .replaceWith(specialCaseParameter)
                    .build(),
                RbelModificationDescription.builder()
                    .targetElement("$.path.[?(content=~'.*bar1')].value")
                    .replaceWith("bar3")
                    .build()
            ))
            .build());

        proxyRest.get("http://backend/foobar?foo=bar1&foo=bar2&schmoo").asString();

        assertThat(getLastRequest().getQueryStringParameters().getEntries())
            .extracting("name")
            .containsOnly("foo", "schmoo");
        assertThat(getLastRequest().getQueryStringParameters().getValues("foo"))
            .containsExactly("bar3", "bar2");
        assertThat(getLastRequest().getQueryStringParameters().getValues("schmoo"))
            .containsExactly(specialCaseParameter);
    }

    @Test
    void reverseProxyWithQueryParameters() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .targetElement("$.path.schmoo.value")
                    .replaceWith("loo")
                    .build(),
                RbelModificationDescription.builder()
                    .targetElement("$.path.[?(content=~'.*bar1')].value")
                    .replaceWith("bar3")
                    .build()))
            .build());

        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar?foo=bar1&foo=bar2&schmoo").asString();

        assertThat(getLastRequest().getQueryStringParameters().getEntries())
            .extracting("name")
            .containsOnly("foo", "schmoo");
        assertThat(getLastRequest().getQueryStringParameters().getValues("foo"))
            .containsExactly("bar3", "bar2");
        assertThat(getLastRequest().getQueryStringParameters().getValues("schmoo"))
            .containsExactly("loo");
    }

    @Test
    void modifyStatusCode_shouldWork() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.responseCode")
                    .replaceWith("200")
                    .build()
            ))
            .build());

        HttpResponse<JsonNode> response = Unirest.get(
            "http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

        assertThat(response.getStatus())
            .isEqualTo(200);
    }

    @Test
    void modifyReasonPhrase_shouldWork() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.reasonPhrase")
                    .replaceWith("Foo bar Bar Foobar")
                    .build()
            ))
            .build());

        HttpResponse<JsonNode> response = Unirest.get(
            "http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

        assertThat(response.getStatusText())
            .isEqualTo("Foo bar Bar Foobar");
    }

    @Test
    void removeReasonPhrase_shouldWork() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort)
                .build()))
            .modifications(List.of(
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.reasonPhrase")
                    .replaceWith(null)
                    .build()
            ))
            .build());

        HttpResponse<JsonNode> response = Unirest.get(
            "http://localhost:" + tigerProxy.getProxyPort() + "/foobar").asJson();

        assertThat(response.getStatusText())
            .isEmpty();
    }
}
