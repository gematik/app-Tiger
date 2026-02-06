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
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestAbstractRouteProxyCallback {

  // Minimal concrete implementation to test abstract class logic
  static class ConcreteRouteProxyCallback extends AbstractRouteProxyCallback {
    public ConcreteRouteProxyCallback(TigerProxy tigerProxy, TigerProxyRoute tigerRoute)
        throws MalformedURLException, URISyntaxException {
      super(tigerProxy, tigerRoute);
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
      return "";
    }

    @Override
    protected String printTrafficTarget(HttpRequest req) {
      return "";
    }

    @Override
    public HttpRequest handleRequest(HttpRequest httpRequest) {
      return httpRequest;
    }
  }

  private TigerProxy createMockProxy() {
    TigerProxy mockProxy = mock(TigerProxy.class);
    RbelLogger rbelLogger = mock(RbelLogger.class);
    RbelConverter rbelConverter = mock(RbelConverter.class);
    when(mockProxy.getRbelLogger()).thenReturn(rbelLogger);
    when(rbelLogger.getRbelConverter()).thenReturn(rbelConverter);
    return mockProxy;
  }

  @ParameterizedTest
  @CsvSource({
    "/, /, /, /",
    "/, /, /VAU/bla, /VAU/bla",
    "/VAU, /VAU, /VAU, /VAU",
    "/VAU, /VAU, /VAU/bla, /VAU/bla",
    "/, , /, /",
    "/, , /VAU/bla, /VAU/bla",
    "/, /deep/path, /foo, /deep/path/foo",
    "/, /, VAU/bla, /VAU/bla",
    "/VAU, /VAU, VAU/bla, /VAU/bla"
  })
  void testPatchPath(String from, String toSuffix, String inputPath, String expectedPath)
      throws MalformedURLException, URISyntaxException {
    TigerProxy mockProxy = createMockProxy();
    String to = "http://gatekeeper:8080" + (toSuffix == null ? "" : toSuffix);
    TigerProxyRoute route = TigerProxyRoute.builder().from(from).to(to).build();

    ConcreteRouteProxyCallback callback = new ConcreteRouteProxyCallback(mockProxy, route);

    assertThat(callback.patchPath(inputPath)).isEqualTo(expectedPath);
  }
}
