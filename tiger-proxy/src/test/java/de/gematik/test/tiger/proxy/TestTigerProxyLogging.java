/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kong.unirest.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyLogging extends AbstractTigerProxyTest {

  @Test
  void useAsWebProxyServer_shouldForward() throws Exception {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    checkForLogMessage(
        () -> proxyRest.get("http://backend/foobar"),
        "Received HTTP GET /foobar",
        "Returning HTTP 666");
    checkForLogMessage(
        () -> proxyRest.post("http://backend/foobar"),
        "Received HTTP POST /foobar",
        "Returning HTTP 200 Response-Length: 185 bytes");
    checkForLogMessage(
        () -> proxyRest.get("http://localhost:" + fakeBackendServerPort + "/schmoobar"),
        "Received HTTP GET /schmoobar",
        "Returning HTTP 404 Response-Length:");
    tigerProxy.getTigerProxyConfiguration().setActivateTrafficLogging(false);
    checkForLogMessage(
        () -> proxyRest.get("http://backend/foobar"),
        a -> a.doesNotContain("Received HTTP GET").doesNotContain("Returning HTTP"));
    tigerProxy.getTigerProxyConfiguration().setActivateTrafficLogging(true);
    checkForLogMessage(
        () -> proxyRest.get("http://backend/foobar"),
        "Received HTTP GET /foobar",
        "Returning HTTP 666");
  }

  private void checkForLogMessage(
      Supplier<HttpRequest> requestSupplier, String... expectedLogMessages) throws Exception {
    checkForLogMessage(requestSupplier, assertion -> assertion.contains(expectedLogMessages));
  }

  private void checkForLogMessage(
      Supplier<HttpRequest> requestSupplier, Consumer<AbstractStringAssert> assertion)
      throws Exception {
    requestSupplier.get().asString();
    assertion.accept(
        assertThat(
            tapSystemOut(
                () -> {
                  tigerProxy.clearAllMessages();
                  requestSupplier.get().asString();
                  log.info("Awaiting message parsing...");
                  awaitMessagesInTiger(2);
                })));
  }
}
