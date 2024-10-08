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
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyExceptionCallbacks extends AbstractTigerProxyTest {

  @Test
  void forwardProxyRequestException_shouldPropagate() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    var caughtExceptionReference = setExceptionThrowingMockRbelConverter(tigerProxy);

    proxyRest.get("http://backend/foobar").asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).contains("foobar");
  }

  @Test
  void reverseProxyRequestException_shouldPropagate() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    var caughtExceptionReference = setExceptionThrowingMockRbelConverter(tigerProxy);

    Unirest.spawnInstance()
        .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).contains("foobar");
  }

  @Test
  void forwardProxyResponseException_shouldPropagate() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());
    var caughtExceptionReference = setExceptionThrowingMockRbelConverter(tigerProxy);

    proxyRest.get("http://backend/foobar").asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).contains("foobar");
  }

  @Test
  void reverseProxyResponseException_shouldPropagate() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    var caughtExceptionReference = setExceptionThrowingMockRbelConverter(tigerProxy);
    Unirest.spawnInstance()
        .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).contains("foobar");
  }

  public static class ExceptionThrowingMockRbelConverter extends MockServerToRbelConverter {

    public ExceptionThrowingMockRbelConverter(RbelConverter rbelConverter) {
      super(rbelConverter);
    }

    @Override
    public CompletableFuture<RbelElement> convertResponse(
        HttpResponse response,
        String senderUrl,
        String clientAddress,
        CompletableFuture<RbelElement> pairedParsedRequest,
        Optional<ZonedDateTime> timestamp) {
      throw new RuntimeException("foobar");
    }
  }

  private AtomicReference<Throwable> setExceptionThrowingMockRbelConverter(TigerProxy tigerProxy) {
    ReflectionTestUtils.setField(
        tigerProxy,
        "mockServerToRbelConverter",
        new ExceptionThrowingMockRbelConverter(tigerProxy.getRbelLogger().getRbelConverter()));
    AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();

    tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);
    return caughtExceptionReference;
  }
}
