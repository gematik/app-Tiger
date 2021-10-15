/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TestTigerProxyModifications extends AbstractTigerProxyTest {

    @Test
    public void replaceStuffForForwardRoute() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
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
                    .replaceWith("{\"another\":{\"node\":{\"path\":\"correctValue\"}}}")
                    .build(),
                RbelModificationDescription.builder()
                    .condition("isResponse")
                    .targetElement("$.header.Matched-Stub-Id")
                    .replaceWith("modified value")
                    .build()
            ))
            .build());

        proxyRest.post("http://backend/notFoobar").asJson();
        assertThat(tigerProxy.getRbelMessages().get(0).findElement("$.header.user-agent"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified user-agent");
        assertThat(tigerProxy.getRbelMessages().get(0).findElement("$.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("/foobar");
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.header.Matched-Stub-Id"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified value");
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body.another.node.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("correctValue");
    }

    @Test
    public void replaceStuffForReverseRoute() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
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

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asJson();

        assertThat(tigerProxy.getRbelMessages().get(0).findElement("$.header.user-agent"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("modified user-agent");
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body.another.node.path"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("correctValue");
    }

    @Test
    public void regexModifications() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
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

        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foobar").asJson();

        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body.foo"))
            .get().extracting(RbelElement::getRawStringContent)
            .isEqualTo("boo");
    }

    @Test
    public void noModificationsForwardProxy_shouldLeaveBinaryContentUntouched() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final byte[] body = proxyRest.post("http://backend/foobar")
            .body(binaryMessageContent)
            .asBytes().getBody();

        assertThat(tigerProxy.getRbelMessages().get(0).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(body).isEqualTo(binaryMessageContent);
    }

    @Test
    public void noModificationsReverseProxy_shouldLeaveBinaryContentUntouched() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        final byte[] body = Unirest.post("http://localhost:" + tigerProxy.getPort() + "/foobar")
            .body(binaryMessageContent)
            .asBytes().getBody();

        assertThat(tigerProxy.getRbelMessages().get(0).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body"))
            .get().extracting(RbelElement::getRawContent)
            .isEqualTo(binaryMessageContent);
        assertThat(body).isEqualTo(binaryMessageContent);
    }

    //TODO test with response status code (not only number)
}
