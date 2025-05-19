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
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kong.unirest.core.HttpRequest;
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
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    checkForLogMessage(
        () -> proxyRest.get("http://backend/foobar"),
        "Received HTTP GET /foobar",
        "Returning HTTP 666");
    checkForLogMessage(
        () -> proxyRest.post("http://backend/foobar"),
        "Received HTTP POST /foobar",
        "Returning HTTP 200 Response-Length:");
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
                  awaitMessagesInTigerProxy(2);
                })));
  }
}
