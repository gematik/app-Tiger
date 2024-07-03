/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTP;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTPS;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.rbellogger.data.RbelElement;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class HttpRequest extends RequestDefinition implements HttpMessage<HttpRequest, Body> {
  private String method = "";
  private String path = "";
  private Parameters pathParameters;
  private Parameters queryStringParameters;
  private Body body = null;
  private Headers headers;
  private Cookies cookies;
  private Boolean keepAlive = null;
  private Boolean secure = null;
  private Protocol protocol = null;
  private Integer streamId = null;
  private List<MockserverX509CertificateWrapper> clientCertificateChain;
  private String tlsVersion = null;
  private String cipherSuite = null;
  private SocketAddress socketAddress;
  private String localAddress;
  private String remoteAddress;
  private Boolean forwardProxyRequest = true;
  private RbelElement parsedRbelMessage = null;

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
      } else if (socketAddress != null && socketAddress.getScheme() != null) {
        setSecure(socketAddress.getScheme() == SocketAddress.Scheme.HTTPS);
      }
    }
    return secure;
  }

  public HttpRequest setSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
    if (socketAddress != null && socketAddress.getScheme() != null) {
      secure = socketAddress.getScheme() == SocketAddress.Scheme.HTTPS;
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
  public HttpRequest setSocketAddress(String host, Integer port, SocketAddress.Scheme scheme) {
    setSocketAddress(new SocketAddress().withHost(host).withPort(port).withScheme(scheme));
    return this;
  }

  public HttpRequest setSocketAddress(Boolean isSecure, String host, Integer port) {
    if (isNotBlank(host)) {
      String[] hostParts = host.split(":");
      boolean secure = Boolean.TRUE.equals(isSecure);
      if (hostParts.length > 1) {
        setSocketAddress(
            hostParts[0],
            port != null ? port : Integer.parseInt(hostParts[1]),
            secure ? HTTPS : HTTP);
      } else if (secure) {
        setSocketAddress(host, port != null ? port : 443, HTTPS);
      } else {
        setSocketAddress(host, port != null ? port : 80, HTTP);
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

  public Parameters getPathParameters() {
    return this.pathParameters;
  }

  public List<Parameter> getQueryStringParameterList() {
    if (getQueryStringParameters() == null || getQueryStringParameters().isEmpty()) {
      return List.of();
    } else {
      return getQueryStringParameters().getEntries();
    }
  }

  public HttpRequest withPathParameters(Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      this.pathParameters = null;
    } else {
      this.pathParameters = parameters;
    }
    return this;
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

  public HttpRequest withBody(String body) {
    this.body = new StringBody(body);
    return this;
  }

  public HttpRequest withBody(String body, Charset charset) {
    if (body != null) {
      this.body = new StringBody(body, charset);
    }
    return this;
  }

  public HttpRequest withBody(byte[] body) {
    this.body = new BinaryBody(body);
    return this;
  }

  public HttpRequest withBody(Body body) {
    this.body = body;
    return this;
  }

  @JsonIgnore
  public byte[] getBodyAsRawBytes() {
    return this.body != null ? this.body.getRawBytes() : new byte[0];
  }

  @JsonIgnore
  public String getBodyAsString() {
    if (body != null) {
      return body.toString();
    } else {
      return null;
    }
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

  public HttpRequest withCookies(Cookies cookies) {
    if (cookies == null || cookies.isEmpty()) {
      this.cookies = null;
    } else {
      this.cookies = cookies;
    }
    return this;
  }

  public List<Cookie> getCookieList() {
    if (this.cookies != null) {
      return this.cookies.getEntries();
    } else {
      return Collections.emptyList();
    }
  }

  public InetSocketAddress socketAddressFromHostHeader() {
    if (socketAddress != null && socketAddress.getHost() != null) {
      boolean isSsl =
          socketAddress.getScheme() != null
              && socketAddress.getScheme().equals(SocketAddress.Scheme.HTTPS);
      return new InetSocketAddress(
          socketAddress.getHost(),
          socketAddress.getPort() != null ? socketAddress.getPort() : isSsl ? 443 : 80);
    } else if (isNotBlank(getFirstHeader(HOST.toString()))) {
      boolean isSsl = isSecure() != null && isSecure();
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

  public HttpRequest shallowClone() {
    return request()
        .setMethod(method)
        .setPath(path)
        .setPathParameters(pathParameters)
        .setQueryStringParameters(queryStringParameters)
        .setBody(body)
        .setHeaders(headers)
        .setCookies(cookies)
        .setKeepAlive(keepAlive)
        .setSecure(secure)
        .setProtocol(protocol)
        .setStreamId(streamId)
        .setClientCertificateChain(clientCertificateChain)
        .setSocketAddress(socketAddress)
        .setLocalAddress(localAddress)
        .setRemoteAddress(remoteAddress);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public HttpRequest clone() {
    return request()
        .setMethod(method)
        .setPath(path)
        .setPathParameters(pathParameters != null ? pathParameters.clone() : null)
        .setQueryStringParameters(
            queryStringParameters != null ? queryStringParameters.clone() : null)
        .withBody(body)
        .withHeaders(headers != null ? headers.clone() : null)
        .withCookies(cookies != null ? cookies.clone() : null)
        .setKeepAlive(keepAlive)
        .setSecure(secure)
        .setProtocol(protocol)
        .setStreamId(streamId)
        .setClientCertificateChain(
            clientCertificateChain != null && !clientCertificateChain.isEmpty()
                ? clientCertificateChain.stream()
                    .map(c -> MockserverX509CertificateWrapper.with(c.certificate()))
                    .toList()
                : null)
        .setSocketAddress(socketAddress)
        .setLocalAddress(localAddress)
        .setRemoteAddress(remoteAddress);
  }

  public String getMethodOrDefault(String fallback) {
    return Optional.ofNullable(method).filter(StringUtils::isNotEmpty).orElse(fallback);
  }
}
