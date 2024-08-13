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

package de.gematik.test.tiger.mockserver.mappers;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

import de.gematik.test.tiger.mockserver.codec.BodyDecoderEncoder;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.model.Cookies;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class FullHttpResponseToMockServerHttpResponse {

  private final BodyDecoderEncoder bodyDecoderEncoder = new BodyDecoderEncoder();

  public HttpResponse mapFullHttpResponseToMockServerResponse(FullHttpResponse fullHttpResponse) {
    HttpResponse httpResponse = new HttpResponse();
    try {
      if (fullHttpResponse != null) {
        if (fullHttpResponse.decoderResult().isFailure()) {
          log.error("exception decoding response ", fullHttpResponse.decoderResult().cause());
        }
        setStatusCode(httpResponse, fullHttpResponse);
        setHeaders(httpResponse, fullHttpResponse);
        setCookies(httpResponse);
        setBody(httpResponse, fullHttpResponse);
      }
    } catch (RuntimeException e) {
      log.error("exception decoding response {}", fullHttpResponse, e);
    }
    return httpResponse;
  }

  private void setStatusCode(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    HttpResponseStatus status = fullHttpResponse.status();
    httpResponse.withStatusCode(status.code());
    httpResponse.withReasonPhrase(status.reasonPhrase());
  }

  private void setHeaders(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    Set<String> headerNames = fullHttpResponse.headers().names();
    if (!headerNames.isEmpty()) {
      Headers headers = new Headers();
      for (String headerName : headerNames) {
        headers.withEntry(headerName, fullHttpResponse.headers().getAll(headerName));
      }
      httpResponse.withHeaders(headers);
    }
  }

  private void setCookies(HttpResponse httpResponse) {
    Cookies cookies = new Cookies();
    for (Header header : httpResponse.getHeaderList()) {
      if (header.getName().equalsIgnoreCase("Set-Cookie")) {
        for (String cookieHeader : header.getValues()) {
          io.netty.handler.codec.http.cookie.Cookie httpCookie =
              ClientCookieDecoder.LAX.decode(cookieHeader);
          String name = httpCookie.name().trim();
          String value = httpCookie.value() != null ? httpCookie.value().trim() : "";
          cookies.withEntry(new Cookie(name, value));
        }
      }
      if (header.getName().equalsIgnoreCase("Cookie")) {
        for (String cookieHeader : header.getValues()) {
          for (io.netty.handler.codec.http.cookie.Cookie httpCookie :
              ServerCookieDecoder.LAX.decode(cookieHeader)) {
            String name = httpCookie.name().trim();
            String value = httpCookie.value() != null ? httpCookie.value().trim() : "";
            cookies.withEntry(new Cookie(name, value));
          }
        }
      }
    }
    if (!cookies.isEmpty()) {
      httpResponse.withCookies(cookies);
    }
  }

  private void setBody(HttpResponse httpResponse, FullHttpResponse fullHttpResponse) {
    httpResponse.withBody(
        bodyDecoderEncoder.byteBufToBody(
            fullHttpResponse.content(), fullHttpResponse.headers().get(CONTENT_TYPE)));
  }
}
