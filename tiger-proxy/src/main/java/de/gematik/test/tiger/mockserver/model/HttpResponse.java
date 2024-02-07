/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import static de.gematik.test.tiger.mockserver.model.Header.header;
import static de.gematik.test.tiger.mockserver.model.HttpStatusCode.NOT_FOUND_404;
import static de.gematik.test.tiger.mockserver.model.HttpStatusCode.OK_200;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Multimap;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/*
 * @author jamesdbloom
 */
@Data
public class HttpResponse extends Action<HttpResponse>
    implements HttpMessage<HttpResponse, BodyWithContentType> {
  private int hashCode;
  private Integer statusCode;
  private String reasonPhrase;
  private BodyWithContentType body;
  private Headers headers;
  private Cookies cookies;
  private Integer streamId = null;

  /** Static builder to create a response. */
  public static HttpResponse response() {
    return new HttpResponse();
  }

  /**
   * Static builder to create a response with a 200 status code and the string response body.
   *
   * @param body a string
   */
  public static HttpResponse response(String body) {
    return new HttpResponse()
        .withStatusCode(OK_200.code())
        .withReasonPhrase(OK_200.reasonPhrase())
        .withBody(body);
  }

  /** Static builder to create a not found response. */
  public static HttpResponse notFoundResponse() {
    return new HttpResponse()
        .withStatusCode(NOT_FOUND_404.code())
        .withReasonPhrase(NOT_FOUND_404.reasonPhrase());
  }

  /**
   * The status code to return, such as 200, 404, the status code specified here will result in the
   * default status message for this status code for example for 200 the status message "OK" is used
   *
   * @param statusCode an integer such as 200 or 404
   */
  public HttpResponse withStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
    this.hashCode = 0;
    return this;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  /**
   * The reason phrase to return, if no reason code is returned this will be defaulted to the
   * standard reason phrase for the statusCode, i.e. for a statusCode of 200 the standard reason
   * phrase is "OK"
   *
   * @param reasonPhrase an string such as "Not Found" or "OK"
   */
  public HttpResponse withReasonPhrase(String reasonPhrase) {
    this.reasonPhrase = reasonPhrase;
    this.hashCode = 0;
    return this;
  }

  public String getReasonPhrase() {
    return reasonPhrase;
  }

  /**
   * Set response body to return as a string response body. The character set will be determined by
   * the Content-Type header on the response. To force the character set, use {@link
   * #withBody(String, Charset)}.
   *
   * @param body a string
   */
  public HttpResponse withBody(String body) {
    if (body != null) {
      this.body = new StringBody(body);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * Set response body to return a string response body with the specified encoding. <b>Note:</b>
   * The character set of the response will be forced to the specified charset, even if the
   * Content-Type header specifies otherwise.
   *
   * @param body a string
   * @param charset character set the string will be encoded in
   */
  public HttpResponse withBody(String body, Charset charset) {
    if (body != null) {
      this.body = new StringBody(body, charset);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * Set response body to return a string response body with the specified encoding. <b>Note:</b>
   * The character set of the response will be forced to the specified charset, even if the
   * Content-Type header specifies otherwise.
   *
   * @param body a string
   * @param contentType media type, if charset is included this will be used for encoding string
   */
  public HttpResponse withBody(String body, MediaType contentType) {
    if (body != null) {
      this.body = new StringBody(body, contentType);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * Set response body to return as binary such as a pdf or image
   *
   * @param body a byte array
   */
  public HttpResponse withBody(byte[] body) {
    this.body = new BinaryBody(body);
    this.hashCode = 0;
    return this;
  }

  /**
   * Set the body to return for example:
   *
   * <p>string body: - exact("<html><head/><body><div>a simple string body</div></body></html>");
   *
   * <p>or
   *
   * <p>- new StringBody("<html><head/><body><div>a simple string body</div></body></html>")
   *
   * <p>binary body: -
   * binary(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"),
   * 1024));
   *
   * <p>or
   *
   * <p>- new
   * BinaryBody(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"),
   * 1024));
   *
   * @param body an instance of one of the Body subclasses including StringBody or BinaryBody
   */
  public HttpResponse withBody(BodyWithContentType body) {
    this.body = body;
    this.hashCode = 0;
    return this;
  }

  public BodyWithContentType getBody() {
    return body;
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

  public Headers getHeaders() {
    return this.headers;
  }

  private Headers getOrCreateHeaders() {
    if (this.headers == null) {
      this.headers = new Headers();
      this.hashCode = 0;
    }
    return this.headers;
  }

  public HttpResponse withHeaders(Headers headers) {
    if (headers == null || headers.isEmpty()) {
      this.headers = null;
    } else {
      this.headers = headers;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * The headers to return as a list of Header objects
   *
   * @param headers a list of Header objects
   */
  public HttpResponse withHeaders(List<Header> headers) {
    getOrCreateHeaders().withEntries(headers);
    this.hashCode = 0;
    return this;
  }

  /**
   * The headers to return as a varargs of Header objects
   *
   * @param headers varargs of Header objects
   */
  public HttpResponse withHeaders(Header... headers) {
    getOrCreateHeaders().withEntries(headers);
    this.hashCode = 0;
    return this;
  }

  /**
   * Add a header to return as a Header object, if a header with the same name already exists this
   * will NOT be modified but two headers will exist
   *
   * @param header a Header object
   */
  public HttpResponse withHeader(Header header) {
    getOrCreateHeaders().withEntry(header);
    this.hashCode = 0;
    return this;
  }

  /**
   * Add a header to return as a Header object, if a header with the same name already exists this
   * will NOT be modified but two headers will exist
   *
   * @param name the header name
   * @param values the header values
   */
  public HttpResponse withHeader(String name, String... values) {
    if (values.length == 0) {
      values = new String[] {".*"};
    }
    getOrCreateHeaders().withEntry(name, values);
    this.hashCode = 0;
    return this;
  }

  /**
   * Update header to return as a Header object, if a header with the same name already exists it
   * will be modified
   *
   * @param header a Header object
   */
  public HttpResponse replaceHeader(Header header) {
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

  public HttpResponse removeHeader(String name) {
    if (this.headers != null) {
      headers.remove(name);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * Returns true if a header with the specified name has been added
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

  public Cookies getCookies() {
    return this.cookies;
  }

  private Cookies getOrCreateCookies() {
    if (this.cookies == null) {
      this.cookies = new Cookies();
      this.hashCode = 0;
    }
    return this.cookies;
  }

  public HttpResponse withCookies(Cookies cookies) {
    if (cookies == null || cookies.isEmpty()) {
      this.cookies = null;
    } else {
      this.cookies = cookies;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * The cookies to return as Set-Cookie headers as a list of Cookie objects
   *
   * @param cookies a list of Cookie objects
   */
  public HttpResponse withCookies(List<Cookie> cookies) {
    getOrCreateCookies().withEntries(cookies);
    this.hashCode = 0;
    return this;
  }

  /**
   * The cookies to return as Set-Cookie headers as a varargs of Cookie objects
   *
   * @param cookies a varargs of Cookie objects
   */
  public HttpResponse withCookies(Cookie... cookies) {
    getOrCreateCookies().withEntries(cookies);
    this.hashCode = 0;
    return this;
  }

  /**
   * Add cookie to return as Set-Cookie header
   *
   * @param cookie a Cookie object
   */
  public HttpResponse withCookie(Cookie cookie) {
    getOrCreateCookies().withEntry(cookie);
    this.hashCode = 0;
    return this;
  }

  /**
   * Add cookie to return as Set-Cookie header
   *
   * @param name the cookies name
   * @param value the cookies value
   */
  public HttpResponse withCookie(String name, String value) {
    getOrCreateCookies().withEntry(new Cookie(name, value));
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

  public boolean cookieHeaderDoesNotAlreadyExists(String name, String value) {
    List<String> setCookieHeaders = getHeader(SET_COOKIE.toString());
    for (String setCookieHeader : setCookieHeaders) {
      String existingCookieName = ClientCookieDecoder.LAX.decode(setCookieHeader).name();
      String existingCookieValue = ClientCookieDecoder.LAX.decode(setCookieHeader).value();
      if (existingCookieName.equalsIgnoreCase(name)
          && existingCookieValue.equalsIgnoreCase(value)) {
        return false;
      }
    }
    return true;
  }

  public HttpResponse withStreamId(Integer streamId) {
    this.streamId = streamId;
    this.hashCode = 0;
    return this;
  }

  public Integer getStreamId() {
    return streamId;
  }

  @Override
  @JsonIgnore
  public Type getType() {
    return Type.RESPONSE;
  }

  public HttpResponse shallowClone() {
    return response()
        .withStatusCode(statusCode)
        .withReasonPhrase(reasonPhrase)
        .withBody(body)
        .withHeaders(headers)
        .withCookies(cookies)
        .withDelay(getDelay())
        .withStreamId(streamId);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public HttpResponse clone() {
    return response()
        .withStatusCode(statusCode)
        .withReasonPhrase(reasonPhrase)
        .withBody(body)
        .withHeaders(headers != null ? headers.clone() : null)
        .withCookies(cookies != null ? cookies.clone() : null)
        .withDelay(getDelay())
        .withStreamId(streamId);
  }

  public Multimap<String, String> getHeaderMultimap() {
    return headers.getMultimap();
  }
}
