/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTP;
import static de.gematik.test.tiger.mockserver.model.SocketAddress.Scheme.HTTPS;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Getter
@Setter
@EqualsAndHashCode
public class HttpRequest extends RequestDefinition implements HttpMessage<HttpRequest, Body> {
  private int hashCode;
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
  private List<X509Certificate> clientCertificateChain;
  private SocketAddress socketAddress;
  private String localAddress;
  private String remoteAddress;

  public static HttpRequest request() {
    return new HttpRequest();
  }

  public static HttpRequest request(String path) {
    return new HttpRequest().withPath(path);
  }

  public Boolean isKeepAlive() {
    return keepAlive;
  }

  /**
   * Match on whether the request was made using an HTTP persistent connection, also called HTTP
   * keep-alive, or HTTP connection reuse
   *
   * @param isKeepAlive true if the request was made with an HTTP persistent connection
   */
  public HttpRequest withKeepAlive(Boolean isKeepAlive) {
    this.keepAlive = isKeepAlive;
    this.hashCode = 0;
    return this;
  }

  public Boolean isSecure() {
    if (socketAddress != null && socketAddress.getScheme() != null) {
      if (socketAddress.getScheme() == SocketAddress.Scheme.HTTPS) {
        secure = true;
        this.hashCode = 0;
      }
    }
    return secure;
  }

  /**
   * Match on whether the request was made over TLS or SSL (i.e. HTTPS)
   *
   * @param isSecure true if the request was made with TLS or SSL
   */
  public HttpRequest withSecure(Boolean isSecure) {
    this.secure = isSecure;
    if (socketAddress != null && socketAddress.getScheme() != null) {
      if (socketAddress.getScheme() == SocketAddress.Scheme.HTTPS) {
        secure = true;
      }
    }
    this.hashCode = 0;
    return this;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  /**
   * Match on whether the request was made over HTTP or HTTP2
   *
   * @param protocol used to indicate HTTP or HTTP2
   */
  public HttpRequest withProtocol(Protocol protocol) {
    this.protocol = protocol;
    this.hashCode = 0;
    return this;
  }

  public Integer getStreamId() {
    return streamId;
  }

  /**
   * HTTP2 stream id request was received on
   *
   * @param streamId HTTP2 stream id request was received on
   */
  public HttpRequest withStreamId(Integer streamId) {
    this.streamId = streamId;
    this.hashCode = 0;
    return this;
  }

  public List<X509Certificate> getClientCertificateChain() {
    return clientCertificateChain;
  }

  public HttpRequest withClientCertificateChain(List<X509Certificate> clientCertificateChain) {
    this.clientCertificateChain = clientCertificateChain;
    this.hashCode = 0;
    return this;
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  /**
   * Specify remote address if the remote address can't be derived from the host header, if no value
   * is specified the host header will be used to determine remote address
   *
   * @param socketAddress the remote address to send request to
   */
  public HttpRequest withSocketAddress(SocketAddress socketAddress) {
    this.socketAddress = socketAddress;
    if (socketAddress != null && socketAddress.getScheme() != null) {
      if (socketAddress.getScheme() == SocketAddress.Scheme.HTTPS) {
        secure = true;
      }
    }
    this.hashCode = 0;
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
  public HttpRequest withSocketAddress(String host, Integer port, SocketAddress.Scheme scheme) {
    this.socketAddress = new SocketAddress().withHost(host).withPort(port).withScheme(scheme);
    this.hashCode = 0;
    return this;
  }

  /**
   * Specify remote address by attempting to derive it from the host header and / or the specified
   * port
   *
   * @param host the remote host or ip to send request to
   * @param port the remote port to send request to
   */
  public HttpRequest withSocketAddress(String host, Integer port) {
    withSocketAddress(secure, host, port);
    this.hashCode = 0;
    return this;
  }

  /** Specify remote address by attempting to derive it from the host header */
  public HttpRequest withSocketAddressFromHostHeader() {
    withSocketAddress(secure, getFirstHeader("host"), null);
    this.hashCode = 0;
    return this;
  }

  /**
   * Specify remote address by attempting to derive it from the host header and / or the specified
   * port
   *
   * @param isSecure true if the request was made with TLS or SSL
   * @param host the remote host or ip to send request to
   * @param port the remote port to send request to
   */
  public HttpRequest withSocketAddress(Boolean isSecure, String host, Integer port) {
    if (isNotBlank(host)) {
      String[] hostParts = host.split(":");
      boolean secure = Boolean.TRUE.equals(isSecure);
      if (hostParts.length > 1) {
        withSocketAddress(
            hostParts[0],
            port != null ? port : Integer.parseInt(hostParts[1]),
            secure ? HTTPS : HTTP);
      } else if (secure) {
        withSocketAddress(host, port != null ? port : 443, HTTPS);
      } else {
        withSocketAddress(host, port != null ? port : 80, HTTP);
      }
    }
    this.hashCode = 0;
    return this;
  }

  public HttpRequest withLocalAddress(String localAddress) {
    this.localAddress = localAddress;
    this.hashCode = 0;
    return this;
  }

  public HttpRequest withRemoteAddress(String remoteAddress) {
    this.remoteAddress = remoteAddress;
    this.hashCode = 0;
    return this;
  }

  public String getRemoteAddress() {
    return remoteAddress;
  }

  /**
   * The HTTP method to match on such as "GET" or "POST"
   *
   * @param method the HTTP method such as "GET" or "POST"
   */
  public HttpRequest withMethod(String method) {
    this.method = method;
    return this;
  }

  /**
   * The path to match on such as "/some_mocked_path" any servlet context path is ignored for
   * matching and should not be specified here regex values are also supported such as ".*_path",
   * see http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html for full details of
   * the supported regex syntax
   *
   * @param path the path such as "/some_mocked_path" or a regex
   */
  public HttpRequest withPath(String path) {
    this.path = path;
    this.hashCode = 0;
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

  private Parameters getOrCreatePathParameters() {
    if (this.pathParameters == null) {
      this.pathParameters = new Parameters();
      this.hashCode = 0;
    }
    return this.pathParameters;
  }

  public HttpRequest withPathParameters(Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      this.pathParameters = null;
    } else {
      this.pathParameters = parameters;
    }
    this.hashCode = 0;
    return this;
  }

  public List<Parameter> getPathParameterList() {
    if (this.pathParameters != null) {
      return this.pathParameters.getEntries();
    } else {
      return Collections.emptyList();
    }
  }

  private Parameters getOrCreateQueryStringParameters() {
    if (this.queryStringParameters == null) {
      this.queryStringParameters = new Parameters();
      this.hashCode = 0;
    }
    return this.queryStringParameters;
  }

  public HttpRequest withQueryStringParameters(Parameters parameters) {
    if (parameters == null || parameters.isEmpty()) {
      this.queryStringParameters = null;
    } else {
      this.queryStringParameters = parameters;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * The query string parameters to match on as a list of Parameter objects where the values or keys
   * of each parameter can be either a string or a regex (for more details of the supported regex
   * syntax see http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param parameters the list of Parameter objects where the values or keys of each parameter can
   *     be either a string or a regex
   */
  public HttpRequest withQueryStringParameters(List<Parameter> parameters) {
    getOrCreateQueryStringParameters().withEntries(parameters);
    this.hashCode = 0;
    return this;
  }

  /**
   * The query string parameters to match on as a varags Parameter objects where the values or keys
   * of each parameter can be either a string or a regex (for more details of the supported regex
   * syntax see http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param parameters the varags Parameter objects where the values or keys of each parameter can
   *     be either a string or a regex
   */
  public HttpRequest withQueryStringParameters(Parameter... parameters) {
    getOrCreateQueryStringParameters().withEntries(parameters);
    this.hashCode = 0;
    return this;
  }

  /**
   * The query string parameters to match on as a Map&lt;String, List&lt;String&gt;&gt; where the
   * values or keys of each parameter can be either a string or a regex (for more details of the
   * supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param parameters the Map&lt;String, List&lt;String&gt;&gt; object where the values or keys of
   *     each parameter can be either a string or a regex
   */
  public HttpRequest withQueryStringParameters(Map<String, List<String>> parameters) {
    getOrCreateQueryStringParameters().withEntries(parameters);
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one query string parameter to match on as a Parameter object where the parameter values
   * list can be a list of strings or regular expressions (for more details of the supported regex
   * syntax see http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param parameter the Parameter object which can have a values list of strings or regular
   *     expressions
   */
  public HttpRequest withQueryStringParameter(Parameter parameter) {
    getOrCreateQueryStringParameters().withEntry(parameter);
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one query string parameter to match which the values are plain strings or regular
   * expressions (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param name the parameter name
   * @param values the parameter values which can be a varags of strings or regular expressions
   */
  public HttpRequest withQueryStringParameter(String name, String... values) {
    getOrCreateQueryStringParameters().withEntry(name, values);
    this.hashCode = 0;
    return this;
  }

  public List<Parameter> getQueryStringParameterList() {
    if (this.queryStringParameters != null) {
      return this.queryStringParameters.getEntries();
    } else {
      return Collections.emptyList();
    }
  }

  @SuppressWarnings("unused")
  public boolean hasQueryStringParameter(String name, String value) {
    if (this.queryStringParameters != null) {
      return this.queryStringParameters.containsEntry(name, value);
    } else {
      return false;
    }
  }

  /**
   * The exact string body to match on such as "this is an exact string body"
   *
   * @param body the body on such as "this is an exact string body"
   */
  public HttpRequest withBody(String body) {
    this.body = new StringBody(body);
    this.hashCode = 0;
    return this;
  }

  /**
   * The exact string body to match on such as "this is an exact string body"
   *
   * @param body the body on such as "this is an exact string body"
   * @param charset character set the string will be encoded in
   */
  public HttpRequest withBody(String body, Charset charset) {
    if (body != null) {
      this.body = new StringBody(body, charset);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * The body to match on as binary data such as a pdf or image
   *
   * @param body a byte array
   */
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

  private Headers getOrCreateHeaders() {
    if (this.headers == null) {
      this.headers = new Headers();
      this.hashCode = 0;
    }
    return this.headers;
  }

  public HttpRequest withHeaders(Headers headers) {
    if (headers == null || headers.isEmpty()) {
      this.headers = null;
    } else {
      this.headers = headers;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * The headers to match on as a list of Header objects where the values or keys of each header can
   * be either a string or a regex (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param headers the list of Header objects where the values or keys of each header can be either
   *     a string or a regex
   */
  public HttpRequest withHeaders(List<Header> headers) {
    getOrCreateHeaders().withEntries(headers);
    this.hashCode = 0;
    return this;
  }

  /**
   * The headers to match on as a varags of Header objects where the values or keys of each header
   * can be either a string or a regex (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param headers the varags of Header objects where the values or keys of each header can be
   *     either a string or a regex
   */
  public HttpRequest withHeaders(Header... headers) {
    getOrCreateHeaders().withEntries(headers);
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one header to match on as a Header object where the header values list can be a list of
   * strings or regular expressions (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param header the Header object which can have a values list of strings or regular expressions
   */
  public HttpRequest withHeader(Header header) {
    getOrCreateHeaders().withEntry(header);
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one header to match which can specified using plain strings or regular expressions (for
   * more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param name the header name
   * @param values the header values which can be a varags of strings or regular expressions
   */
  public HttpRequest withHeader(String name, String... values) {
    if (values.length == 0) {
      values = new String[] {".*"};
    }
    getOrCreateHeaders().withEntry(header(name, values));
    this.hashCode = 0;
    return this;
  }

  public HttpRequest withContentType(MediaType mediaType) {
    getOrCreateHeaders().withEntry(header(CONTENT_TYPE.toString(), mediaType.toString()));
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one header to match on as a Header object where the header values list can be a list of
   * strings or regular expressions (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param header the Header object which can have a values list of strings or regular expressions
   */
  public HttpRequest replaceHeader(Header header) {
    getOrCreateHeaders().replaceEntry(header);
    this.hashCode = 0;
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

  /**
   * Returns true if a header with the specified name has been added
   *
   * @param name the header name
   * @return true if a header has been added with that name otherwise false
   */
  public boolean containsHeader(String name) {
    if (this.headers != null) {
      return this.headers.containsEntry(name);
    } else {
      return false;
    }
  }

  /**
   * Returns true if a header with the specified name and value has been added
   *
   * @param name the header name
   * @param value the header value
   * @return true if a header has been added with that name otherwise false
   */
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
      this.hashCode = 0;
    }
    return this;
  }

  private Cookies getOrCreateCookies() {
    if (this.cookies == null) {
      this.cookies = new Cookies();
      this.hashCode = 0;
    }
    return this.cookies;
  }

  public HttpRequest withCookies(Cookies cookies) {
    if (cookies == null || cookies.isEmpty()) {
      this.cookies = null;
    } else {
      this.cookies = cookies;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * The cookies to match on as a list of Cookie objects where the values or keys of each cookie can
   * be either a string or a regex (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param cookies a list of Cookie objects
   */
  public HttpRequest withCookies(List<Cookie> cookies) {
    getOrCreateCookies().withEntries(cookies);
    this.hashCode = 0;
    return this;
  }

  /**
   * The cookies to match on as a varags Cookie objects where the values or keys of each cookie can
   * be either a string or a regex (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param cookies a varargs of Cookie objects
   */
  public HttpRequest withCookies(Cookie... cookies) {
    getOrCreateCookies().withEntries(cookies);
    this.hashCode = 0;
    return this;
  }

  /**
   * Adds one cookie to match on as a Cookie object where the cookie values list can be a list of
   * strings or regular expressions (for more details of the supported regex syntax see
   * http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)
   *
   * @param cookie a Cookie object
   */
  public HttpRequest withCookie(Cookie cookie) {
    getOrCreateCookies().withEntry(cookie);
    this.hashCode = 0;
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
    return not(request(), not)
        .withMethod(method)
        .withPath(path)
        .withPathParameters(pathParameters)
        .withQueryStringParameters(queryStringParameters)
        .withBody(body)
        .withHeaders(headers)
        .withCookies(cookies)
        .withKeepAlive(keepAlive)
        .withSecure(secure)
        .withProtocol(protocol)
        .withStreamId(streamId)
        .withClientCertificateChain(clientCertificateChain)
        .withSocketAddress(socketAddress)
        .withLocalAddress(localAddress)
        .withRemoteAddress(remoteAddress);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public HttpRequest clone() {
    return not(request(), not)
        .withMethod(method)
        .withPath(path)
        .withPathParameters(pathParameters != null ? pathParameters.clone() : null)
        .withQueryStringParameters(
            queryStringParameters != null ? queryStringParameters.clone() : null)
        .withBody(body)
        .withHeaders(headers != null ? headers.clone() : null)
        .withCookies(cookies != null ? cookies.clone() : null)
        .withKeepAlive(keepAlive)
        .withSecure(secure)
        .withProtocol(protocol)
        .withStreamId(streamId)
        .withClientCertificateChain(
            clientCertificateChain != null && !clientCertificateChain.isEmpty()
                ? clientCertificateChain.stream()
                    .map(X509Certificate::clone)
                    .collect(Collectors.toList())
                : null)
        .withSocketAddress(socketAddress)
        .withLocalAddress(localAddress)
        .withRemoteAddress(remoteAddress);
  }

  public String getMethodOrDefault(String fallback) {
    return Optional.ofNullable(method).filter(StringUtils::isNotEmpty).orElse(fallback);
  }
}
