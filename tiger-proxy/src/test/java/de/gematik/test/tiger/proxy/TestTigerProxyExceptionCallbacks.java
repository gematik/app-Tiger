/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TestTigerProxyExceptionCallbacks extends AbstractTigerProxyTest {

    @Test
    public void forwardProxyRequestException_shouldPropagate() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        tigerProxy.getRbelLogger().getRbelConverter().addConverter((el, c) -> {
            throw new RuntimeException("foobar");
        });
        AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
        tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

        proxyRest.get("http://backend/foobar").asString();

        assertThat(caughtExceptionReference.get().getMessage())
            .isEqualTo("foobar");
    }

    @Test
    public void reverseProxyRequestException_shouldPropagate() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        tigerProxy.getRbelLogger().getRbelConverter().addConverter((el, c) -> {
            throw new RuntimeException("foobar");
        });
        AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
        tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

        Unirest.spawnInstance().get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(caughtExceptionReference.get().getMessage())
            .isEqualTo("foobar");
    }

    @Test
    public void forwardProxyResponseException_shouldPropagate() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        tigerProxy.getRbelLogger().getRbelConverter().addConverter((el, c) -> {
            if (el.hasFacet(RbelHttpResponseFacet.class)) {
                throw new RuntimeException("foobar");
            }
        });
        AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
        tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

        proxyRest.get("http://backend/foobar").asString();

        assertThat(caughtExceptionReference.get().getMessage())
            .isEqualTo("foobar");
    }

    @Test
    public void reverseProxyResponseException_shouldPropagate() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        tigerProxy.getRbelLogger().getRbelConverter().addConverter((el, c) -> {
            if (el.hasFacet(RbelHttpResponseFacet.class)) {
                throw new RuntimeException("foobar");
            }
        });
        AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
        tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

        Unirest.spawnInstance().get("http://localhost:" + tigerProxy.getPort() + "/foobar").asString();

        assertThat(caughtExceptionReference.get().getMessage())
            .isEqualTo("foobar");
    }
}
