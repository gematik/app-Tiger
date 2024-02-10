/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mappers;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

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
    int statusCode = httpResponse.getStatusCode() != null ? httpResponse.getStatusCode() : 200;
    if (!isEmpty(httpResponse.getReasonPhrase())) {
      return new HttpResponseStatus(statusCode, httpResponse.getReasonPhrase());
    } else {
      return HttpResponseStatus.valueOf(statusCode);
    }
  }

  private ByteBuf getBody(HttpResponse httpResponse) {
    return bodyDecoderEncoder.bodyToByteBuf(
        httpResponse.getBody(), httpResponse.getFirstHeader(CONTENT_TYPE.toString()));
  }

  private void setHeaders(HttpResponse httpResponse, DefaultHttpResponse response, ByteBuf body) {
    if (httpResponse.getHeaders() != null) {
      httpResponse
          .getHeaderMultimap()
          .entries()
          .forEach(entry -> response.headers().add(entry.getKey(), entry.getValue()));
    }

    // Content-Type
    if (isBlank(httpResponse.getFirstHeader(CONTENT_TYPE.toString()))) {
      if (httpResponse.getBody() != null && httpResponse.getBody().getContentType() != null) {
        response.headers().set(CONTENT_TYPE, httpResponse.getBody().getContentType());
      }
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
