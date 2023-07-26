/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.stream.Stream;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyRedirecting extends AbstractFastTigerProxyTest {

    @ParameterizedTest
    @MethodSource("nestedAndShallowPathTestCases")
    void forwardProxyWithNestedPath_shouldRewriteLocationHeaderIfConfigured(String toPath, String requestPath, int statusCode) {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("http://foo.bar")
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

        assertThat(proxyRest.get("http://foo.bar" + requestPath)
            .asString().getStatus())
            .isEqualTo(statusCode); // we should be forwarded to /deep/foobar
    }

    @ParameterizedTest
    @MethodSource("nestedAndShallowPathTestCases")
    void reverseProxyWithNestedPath_shouldRewriteLocationHeaderIfConfigured(String toPath, String requestPath, int statusCode) {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
            .asString().getStatus())
            .isEqualTo(statusCode); // we should be forwarded to /deep/foobar
    }

    public static Stream<Arguments> nestedAndShallowPathTestCases() {
        return Stream.of(
            Arguments.of("/deep", "/forward", 777),
            Arguments.of("/deep", "/forward/", 777),
            Arguments.of("/deep/", "/forward", 777),
            Arguments.of("/deep/", "/forward/", 777),

            Arguments.of("", "/forward", 666),
            Arguments.of("", "/forward/", 666),
            Arguments.of("/", "/forward", 666),
            Arguments.of("/", "/forward/", 666));
    }
}
