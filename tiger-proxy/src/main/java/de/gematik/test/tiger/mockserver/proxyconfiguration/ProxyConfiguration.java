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

package de.gematik.test.tiger.mockserver.proxyconfiguration;

import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.ObjectWithJsonToString;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/*
 * @author jamesdbloom
 */
@Getter
public class ProxyConfiguration extends ObjectWithJsonToString {

  private final Type type;
  private final InetSocketAddress proxyAddress;
  private final String username;
  private final String password;
  private final List<String> noProxyHosts = new ArrayList<>();

  private ProxyConfiguration(
      Type type, InetSocketAddress proxyAddress, String username, String password) {
    this.type = type;
    this.proxyAddress = proxyAddress;
    this.username = username;
    this.password = password;
  }

  public static ProxyConfiguration proxyConfiguration(Type type, String address) {
    return proxyConfiguration(type, address, null, null);
  }

  public static ProxyConfiguration proxyConfiguration(Type type, InetSocketAddress address) {
    return proxyConfiguration(type, address, null, null);
  }

  public static ProxyConfiguration proxyConfiguration(
      Type type, String address, String username, String password) {
    String[] addressParts = address.split(":");
    if (addressParts.length != 2) {
      throw new IllegalArgumentException(
          "Proxy address must be in the format <host>:<ip>, for example 127.0.0.1:9090 or"
              + " localhost:9090");
    } else {
      try {
        return proxyConfiguration(
            type,
            new InetSocketAddress(addressParts[0], Integer.parseInt(addressParts[1])),
            username,
            password);
      } catch (NumberFormatException nfe) {
        throw new IllegalArgumentException(
            "Proxy address port \"" + addressParts[1] + "\" into an integer");
      }
    }
  }

  public static ProxyConfiguration proxyConfiguration(
      Type type, InetSocketAddress address, String username, String password) {
    return new ProxyConfiguration(type, address, username, password);
  }

  @SuppressWarnings("UnusedReturnValue")
  public ProxyConfiguration addProxyAuthenticationHeader(HttpRequest httpRequest) {
    if (isNotBlank(username) && isNotBlank(password)) {
      httpRequest.withHeader(
          PROXY_AUTHORIZATION.toString(),
          "Basic "
              + Base64.encode(
                      Unpooled.copiedBuffer(username + ':' + password, StandardCharsets.UTF_8),
                      false)
                  .toString(StandardCharsets.US_ASCII));
    }
    return this;
  }

  public enum Type {
    HTTP,
    HTTPS,
    SOCKS5
  }
}
