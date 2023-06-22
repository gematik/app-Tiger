/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.*;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import kong.unirest.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.mockserver.model.SocketAddress;
import org.mockserver.netty.MockServer;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyRouting extends AbstractTigerProxyTest {

    @ParameterizedTest
    @MethodSource("nestedAndShallowPathTestCases")
    void forwardProxyToNestedTarget_ShouldAdressCorrectly(String fromPath,
        String requestPath, String actualPath, int expectedReturnCode) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServerPort + fromPath)
                .build()))
            .build());

        assertThat(proxyRest.get("http://backend" + requestPath).asString().getStatus())
            .isEqualTo(expectedReturnCode);
        awaitMessagesInTiger(2);
        final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

        assertThat(request)
            .extractChildWithPath("$.header.Host")
            .hasStringContentEqualTo("localhost:" + fakeBackendServerPort);
        assertThat(request)
            .extractChildWithPath("$.path")
            .hasStringContentEqualTo(actualPath);
    }

    @ParameterizedTest
    @MethodSource("nestedAndShallowPathTestCases")
    void reverseProxyToNestedTarget_ShouldAddressCorrectly(String fromPath,
        String requestPath, String actualPath, int expectedReturnCode) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + fakeBackendServerPort + fromPath)
                .build()))
            .build());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
            .asString().getStatus())
            .isEqualTo(expectedReturnCode);
        awaitMessagesInTiger(2);
        final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

        assertThat(request)
            .extractChildWithPath("$.header.[?(key=~'host|Host')]")
            .hasStringContentEqualTo("localhost:" + tigerProxy.getProxyPort());
        assertThat(tigerProxy.getRbelMessagesList().get(0))
            .extractChildWithPath("$.path")
            .hasStringContentEqualTo(actualPath);
    }

    public static Stream<Arguments> nestedAndShallowPathTestCases() {
        return Stream.of(
            Arguments.of("/deep","/foobar","/deep/foobar", 777),
            Arguments.of("/deep","/foobar/","/deep/foobar/", 777),
            Arguments.of("/deep/","/foobar","/deep/foobar", 777),
            Arguments.of("/deep/","/foobar/","/deep/foobar/", 777),
            //"/foobar,'','/foobar'", //TODO TGR-949: mockserver-bug. should work
            Arguments.of("/foobar","/","/foobar/", 666),
            Arguments.of("/foobar/","","/foobar/", 666),
            Arguments.of("/foobar/","/","/foobar/", 666),
            Arguments.of("","/foobar","/foobar", 666),
            Arguments.of("","/foobar/","/foobar/", 666),
            Arguments.of("/","/foobar","/foobar", 666),
            Arguments.of("/","/foobar/","/foobar/", 666),
            //"'','',''", //TODO TGR-949: mockserver-bug. should work
            Arguments.of("","/","/", 888),
            Arguments.of("/","","/", 888),
            Arguments.of("/","/","/", 888));
    }
}
