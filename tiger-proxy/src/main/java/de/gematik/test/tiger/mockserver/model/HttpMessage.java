package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import java.util.List;

@SuppressWarnings("rawtypes")
public interface HttpMessage<T extends HttpMessage, B extends Body> extends Message {

  T withBody(String body);

  T withBody(String body, Charset charset);

  T withBody(byte[] body);

  T withBody(B body);

  B getBody();

  @JsonIgnore
  byte[] getBodyAsRawBytes();

  @JsonIgnore
  String getBodyAsString();

  Headers getHeaders();

  T withHeaders(Headers headers);

  T withHeader(Header header);

  T withHeader(String name, String... values);

  T replaceHeader(Header header);

  List<Header> getHeaderList();

  List<String> getHeader(String name);

  String getFirstHeader(String name);

  T removeHeader(String name);

  Cookies getCookies();

  T withCookies(Cookies cookies);

  List<Cookie> getCookieList();
}
