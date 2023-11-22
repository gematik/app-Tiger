/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.model.HttpResponse;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyExceptionCallbacks extends AbstractTigerProxyTest {

  @Test
  void forwardProxyRequestException_shouldPropagate() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());
    ReflectionTestUtils.setField(
        tigerProxy,
        "mockServerToRbelConverter",
        new ExceptionThrowingMockRbelConverter(tigerProxy.getRbelLogger().getRbelConverter()));
    AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
    tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

    proxyRest.get("http://backend/foobar").asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).isEqualTo("foobar");
  }

  @Test
  void reverseProxyRequestException_shouldPropagate() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());
    ReflectionTestUtils.setField(
        tigerProxy,
        "mockServerToRbelConverter",
        new ExceptionThrowingMockRbelConverter(tigerProxy.getRbelLogger().getRbelConverter()));
    AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
    tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

    Unirest.spawnInstance()
        .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).isEqualTo("foobar");
  }

  @Test
  void forwardProxyResponseException_shouldPropagate() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());
    ReflectionTestUtils.setField(
        tigerProxy,
        "mockServerToRbelConverter",
        new ExceptionThrowingMockRbelConverter(tigerProxy.getRbelLogger().getRbelConverter()));
    AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
    tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

    proxyRest.get("http://backend/foobar").asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).isEqualTo("foobar");
  }

  @Test
  void reverseProxyResponseException_shouldPropagate() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());
    ReflectionTestUtils.setField(
        tigerProxy,
        "mockServerToRbelConverter",
        new ExceptionThrowingMockRbelConverter(tigerProxy.getRbelLogger().getRbelConverter()));
    AtomicReference<Throwable> caughtExceptionReference = new AtomicReference<>();
    tigerProxy.addNewExceptionConsumer(caughtExceptionReference::set);

    Unirest.spawnInstance()
        .get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
        .asString();
    await().until(() -> caughtExceptionReference.get() != null);

    assertThat(caughtExceptionReference.get().getMessage()).isEqualTo("foobar");
  }

  public static class ExceptionThrowingMockRbelConverter extends MockServerToRbelConverter {

    public ExceptionThrowingMockRbelConverter(RbelConverter rbelConverter) {
      super(rbelConverter);
    }

    @Override
    public RbelElement convertResponse(
        HttpResponse response, String serverProtocolAndHost, String clientAddress) {
      throw new RuntimeException("foobar");
    }
  }
}
