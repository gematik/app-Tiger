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

package de.gematik.test.tiger.mockserver.model;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTP;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTPS;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.rbellogger.data.RbelElement;
import java.net.InetSocketAddress;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequest extends HttpMessage<HttpRequest> {
  private String method = "";
  private String path = "";
  private Parameters pathParameters;
  private Parameters queryStringParameters;
  private Headers headers;
  private Boolean keepAlive = null;
  private Boolean secure = null;
  private HttpProtocol protocol = null;
  private Integer streamId = null;
  private List<MockserverX509CertificateWrapper> clientCertificateChain;
  private String tlsVersion = null;
  private String cipherSuite = null;
  private SocketAddress receiverAddress;
  private String senderAddress;
  private Boolean forwardProxyRequest = false;
  private RbelElement parsedRbelMessage = null;
  private String logCorrelationId;

  public static HttpRequest request() {
    return new HttpRequest();
  }

  public static HttpRequest request(String path) {
    return new HttpRequest().setPath(path);
  }

  public Boolean isSecure() {
    if (secure == null) {
      if (tlsVersion != null || cipherSuite != null) {
        setSecure(true);
      } else if (receiverAddress != null && receiverAddress.getScheme() != null) {
        setSecure(receiverAddress.getScheme() == SocketAddress.Scheme.HTTPS);
      }
    }
    return secure;
  }

  public HttpRequest setReceiverAddress(SocketAddress receiverAddress) {
    this.receiverAddress = receiverAddress;
    if (receiverAddress != null && receiverAddress.getScheme() != null) {
      secure = receiverAddress.getScheme() == SocketAddress.Scheme.HTTPS;
    }
    return this;
  }

  /**
   * Specify remote address if the remote address can't be derived from the host header, if no value
   * is specified the host header will be used to determine remote address
   *
   * @param host the remote host or ip to send request to
   * @param port the remote port to send request to
   * @param scheme the scheme to use for remote socket
   */
  public HttpRequest setReceiverAddress(String host, Integer port, SocketAddress.Scheme scheme) {
    setReceiverAddress(new SocketAddress().withHost(host).withPort(port).withScheme(scheme));
    return this;
  }

  public HttpRequest setReceiverAddress(Boolean isSecure, String host, Integer port) {
    if (isNotBlank(host)) {
      String[] hostParts = host.split(":");
      boolean secure = Boolean.TRUE.equals(isSecure);
      if (hostParts.length > 1) {
        setReceiverAddress(
            hostParts[0],
            port != null ? port : Integer.parseInt(hostParts[1]),
            secure ? HTTPS : HTTP);
      } else if (secure) {
        setReceiverAddress(host, port != null ? port : 443, HTTPS);
      } else {
        setReceiverAddress(host, port != null ? port : 80, HTTP);
      }
    }
    return this;
  }

  public boolean matches(final String method) {
    return this.method.equals(method);
  }

  public boolean matches(final String method, final String... paths) {
    boolean matches = false;
    for (String path : paths) {
      matches = this.method.equals(method) && this.path.equals(path);
      if (matches) {
        break;
      }
    }
    return matches;
  }

  public List<Parameter> getQueryStringParameterList() {
    if (getQueryStringParameters() == null || getQueryStringParameters().isEmpty()) {
      return List.of();
    } else {
      return getQueryStringParameters().getEntries();
    }
  }

  private Parameters getOrCreateQueryStringParameters() {
    if (this.queryStringParameters == null) {
      this.queryStringParameters = new Parameters();
    }
    return this.queryStringParameters;
  }

  public HttpRequest withQueryStringParameter(String name, String... values) {
    getOrCreateQueryStringParameters().withEntry(name, values);
    return this;
  }

  public HttpRequest withBody(byte[] body) {
    setBody(body);
    return this;
  }

  @Override
  public Headers getHeaders() {
    if (this.headers == null) {
      this.headers = new Headers();
    }
    return this.headers;
  }

  public HttpRequest withHeaders(Headers headers) {
    if (headers == null || headers.isEmpty()) {
      this.headers = null;
    } else {
      this.headers = headers;
    }
    return this;
  }

  public HttpRequest withHeader(Header header) {
    getHeaders().withEntry(header);
    return this;
  }

  public HttpRequest withHeader(String name, String... values) {
    if (values.length == 0) {
      values = new String[] {".*"};
    }
    getHeaders().withEntry(header(name, values));
    return this;
  }

  public HttpRequest replaceHeader(Header header) {
    getHeaders().replaceEntry(header);
    return this;
  }

  public List<Header> getHeaderList() {
    if (this.headers != null) {
      return this.headers.getEntries();
    } else {
      return Collections.emptyList();
    }
  }

  public List<String> getHeader(String name) {
    if (this.headers != null) {
      return this.headers.getValues(name);
    } else {
      return Collections.emptyList();
    }
  }

  public String getFirstHeader(String name) {
    if (this.headers != null) {
      return this.headers.getFirstValue(name);
    } else {
      return "";
    }
  }

  public boolean containsHeader(String name, String value) {
    if (this.headers != null) {
      return this.headers.containsEntry(name, value);
    } else {
      return false;
    }
  }

  public HttpRequest removeHeader(String name) {
    if (this.headers != null) {
      headers.remove(name);
    }
    return this;
  }

  public InetSocketAddress socketAddressFromHostHeader() {
    if (receiverAddress != null && receiverAddress.getHost() != null) {
      boolean isSsl =
          receiverAddress.getScheme() != null
              && receiverAddress.getScheme().equals(SocketAddress.Scheme.HTTPS);
      return new InetSocketAddress(
          receiverAddress.getHost(),
          receiverAddress.getPort() != null ? receiverAddress.getPort() : isSsl ? 443 : 80);
    } else if (isNotBlank(getFirstHeader(HOST.toString()))) {
      boolean isSsl = Optional.ofNullable(isSecure()).orElse(false);
      String[] hostHeaderParts = getFirstHeader(HOST.toString()).split(":");
      return new InetSocketAddress(
          hostHeaderParts[0],
          hostHeaderParts.length > 1 ? Integer.parseInt(hostHeaderParts[1]) : isSsl ? 443 : 80);
    } else {
      throw new IllegalArgumentException(
          "Host header must be provided to determine remote socket address, the request does not"
              + " include the \"Host\" header:"
              + NEW_LINE
              + this);
    }
  }

  public String getMethodOrDefault(String fallback) {
    return Optional.ofNullable(method).filter(StringUtils::isNotEmpty).orElse(fallback);
  }

  public String printLogLineDescription() {
    return createProtocolString()
        + " "
        + getMethod()
        + " "
        + getPath()
        + " "
        + createHostHeaderString()
        + " "
        + createUserAgentString()
        + " Request-Length: "
        + createMessageSizeString();
  }

  private String createProtocolString() {
    if (Boolean.TRUE.equals(isSecure())) {
      return "HTTPS";
    } else {
      return "HTTP";
    }
  }

  private String createHostHeaderString() {
    return Optional.ofNullable(getFirstHeader("host"))
        .filter(StringUtils::isNotEmpty)
        .map(agent -> "Host: '" + agent + "'")
        .orElse("");
  }

  private String createUserAgentString() {
    return Optional.ofNullable(getFirstHeader("User-Agent"))
        .filter(StringUtils::isNotEmpty)
        .map(agent -> "User-Agent: '" + agent + "'")
        .orElse("");
  }

  private String createMessageSizeString() {
    if (getBody() == null) {
      return "0 bytes";
    } else {
      return FileUtils.byteCountToDisplaySize(getBody().length);
    }
  }
}
