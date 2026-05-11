/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.test.tiger.mockserver.model.CloseChannel;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the self-referencing detection (cannedResponse / targetsSelf) in {@link
 * AbstractFallbackRouteCallback}, exercised through both {@link ForwardAllCallback} and {@link
 * HostHeaderForwardCallback}.
 */
class TestSelfReferencingDetection {

  private ForwardAllCallback forwardAllCallback;
  private HostHeaderForwardCallback hostHeaderForwardCallback;

  @BeforeEach
  void setUp() {
    TigerProxy tigerProxy = mock(TigerProxy.class);
    var rbelLogger = mock(RbelLogger.class);
    var rbelConverter = mock(RbelConverter.class);
    when(tigerProxy.getRbelLogger()).thenReturn(rbelLogger);
    when(rbelLogger.getRbelConverter()).thenReturn(rbelConverter);
    when(tigerProxy.getProxyPort()).thenReturn(8080);

    forwardAllCallback = new ForwardAllCallback(tigerProxy);
    hostHeaderForwardCallback = new HostHeaderForwardCallback(tigerProxy);
  }

  static Stream<AbstractTigerRouteCallback> callbacks() {
    return Stream.empty(); // populated via @BeforeEach — see callbacksFromInstance()
  }

  Stream<AbstractTigerRouteCallback> allCallbacks() {
    return Stream.of(forwardAllCallback, hostHeaderForwardCallback);
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "127.0.0.1"})
  void cannedResponse_selfReferencing_shouldReturnCloseChannel(String host) {
    var request = HttpRequest.request().withHeader("Host", host + ":8080");
    allCallbacks()
        .forEach(
            callback ->
                assertThat(callback.cannedResponse(request))
                    .as(
                        "cannedResponse for %s with host %s",
                        callback.getClass().getSimpleName(), host)
                    .isPresent()
                    .get()
                    .isInstanceOf(CloseChannel.class));
  }

  @Test
  void cannedResponse_differentHost_shouldReturnEmpty() {
    var request = HttpRequest.request().withHeader("Host", "example.com:9090");
    allCallbacks()
        .forEach(
            callback ->
                assertThat(callback.cannedResponse(request))
                    .as("cannedResponse for %s", callback.getClass().getSimpleName())
                    .isEmpty());
  }

  @Test
  void cannedResponse_sameHostDifferentPort_shouldReturnEmpty() {
    var request = HttpRequest.request().withHeader("Host", "localhost:9999");
    allCallbacks()
        .forEach(
            callback ->
                assertThat(callback.cannedResponse(request))
                    .as("cannedResponse for %s", callback.getClass().getSimpleName())
                    .isEmpty());
  }

  @Test
  void cannedResponse_noHostHeader_shouldReturnEmpty() {
    var request = HttpRequest.request();
    allCallbacks()
        .forEach(
            callback ->
                assertThat(callback.cannedResponse(request))
                    .as("cannedResponse for %s", callback.getClass().getSimpleName())
                    .isEmpty());
  }

  @Test
  void hostHeaderForwardCallback_matches_withValidHostHeader_shouldReturnTrue() {
    var request = HttpRequest.request().withHeader("Host", "example.com:9090");
    assertThat(hostHeaderForwardCallback.matches(request)).isTrue();
  }

  @Test
  void hostHeaderForwardCallback_matches_withEmptyHostHeader_shouldReturnFalse() {
    var request = HttpRequest.request().withHeader("Host", "");
    assertThat(hostHeaderForwardCallback.matches(request)).isFalse();
  }

  @Test
  void hostHeaderForwardCallback_matches_withNoHostHeader_shouldReturnFalse() {
    var request = HttpRequest.request();
    assertThat(hostHeaderForwardCallback.matches(request)).isFalse();
  }
}
