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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestForwardAllCallback {

  private TigerProxy tigerProxy;
  private ForwardAllCallback callback;

  @BeforeEach
  void setUp() {
    tigerProxy = mock(TigerProxy.class);
    var rbelLogger = mock(RbelLogger.class);
    var rbelConverter = mock(RbelConverter.class);
    when(tigerProxy.getRbelLogger()).thenReturn(rbelLogger);
    when(rbelLogger.getRbelConverter()).thenReturn(rbelConverter);
    when(tigerProxy.getProxyPort()).thenReturn(8080);

    callback = new ForwardAllCallback(tigerProxy);
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "127.0.0.1"})
  void cannedResponse_selfReferencing_shouldReturnCloseChannel(String host) {
    var request = HttpRequest.request().withHeader("Host", host + ":8080");
    assertThat(callback.cannedResponse(request)).isPresent().get().isInstanceOf(CloseChannel.class);
  }

  @Test
  void cannedResponse_differentHost_shouldReturnEmpty() {
    var request = HttpRequest.request().withHeader("Host", "example.com:9090");
    assertThat(callback.cannedResponse(request)).isEmpty();
  }

  @Test
  void cannedResponse_sameHostDifferentPort_shouldReturnEmpty() {
    var request = HttpRequest.request().withHeader("Host", "localhost:9999");
    assertThat(callback.cannedResponse(request)).isEmpty();
  }
}
