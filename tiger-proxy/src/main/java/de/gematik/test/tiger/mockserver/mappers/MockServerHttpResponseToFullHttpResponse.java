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

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.apache.commons.lang3.StringUtils.isBlank;

import de.gematik.test.tiger.mockserver.codec.BodyDecoderEncoder;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.HttpConversionUtil;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class MockServerHttpResponseToFullHttpResponse {

  private final BodyDecoderEncoder bodyDecoderEncoder = new BodyDecoderEncoder();

  public List<DefaultHttpObject> mapMockServerResponseToNettyResponse(HttpResponse httpResponse) {
    try {
      ByteBuf body = getBody(httpResponse);
      DefaultFullHttpResponse defaultFullHttpResponse =
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, getStatus(httpResponse), body);
      setHeaders(httpResponse, defaultFullHttpResponse, body);
      return Collections.singletonList(defaultFullHttpResponse);
    } catch (Exception e) {
      log.error("exception encoding response{}", httpResponse, e);
      return Collections.singletonList(
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, getStatus(httpResponse)));
    }
  }

  private HttpResponseStatus getStatus(HttpResponse httpResponse) {
    final int statusCode =
        httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200;
    final String reasonPhrase =
        httpResponse.getReasonPhrase() != null ? httpResponse.getReasonPhrase() : "";
    return new HttpResponseStatus(statusCode, reasonPhrase);
  }

  private ByteBuf getBody(HttpResponse httpResponse) {
    return bodyDecoderEncoder.bodyToByteBuf(httpResponse.getBody());
  }

  private void setHeaders(HttpResponse httpResponse, DefaultHttpResponse response, ByteBuf body) {
    if (httpResponse.getHeaders() != null) {
      httpResponse
          .getHeaderMultimap()
          .entries()
          .forEach(entry -> response.headers().add(entry.getKey(), entry.getValue()));
    }

    // Content-Length
    if (isBlank(httpResponse.getFirstHeader(CONTENT_LENGTH.toString()))) {
      boolean chunkedEncoding = response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING);
      if (chunkedEncoding) {
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
      } else {
        response.headers().set(CONTENT_LENGTH, body.readableBytes());
      }
    }

    // HTTP2 extension headers
    Integer streamId = httpResponse.getStreamId();
    if (streamId != null) {
      response.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
    }
  }
}
