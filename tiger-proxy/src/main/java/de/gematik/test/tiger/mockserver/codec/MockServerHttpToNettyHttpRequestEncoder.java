/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import java.util.Map;

/*
 * @author jamesdbloom
 */
public class MockServerHttpToNettyHttpRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

  private final MockServerHttpRequestToFullHttpRequest mockServerHttpRequestToFullHttpRequest;

  MockServerHttpToNettyHttpRequestEncoder(
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    mockServerHttpRequestToFullHttpRequest =
        new MockServerHttpRequestToFullHttpRequest(proxyConfigurations);
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
    out.add(mockServerHttpRequestToFullHttpRequest.mapMockServerRequestToNettyRequest(httpRequest));
  }
}
