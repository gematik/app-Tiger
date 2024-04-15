/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.model.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class BodyDecoderEncoder {

  public ByteBuf bodyToByteBuf(Body body, String contentTypeHeader) {
    byte[] bytes = bodyToBytes(body, contentTypeHeader);
    if (bytes != null) {
      return Unpooled.copiedBuffer(bytes);
    } else {
      return Unpooled.buffer(0, 0);
    }
  }

  byte[] bodyToBytes(Body body, String contentTypeHeader) {
    if (body != null) {
      if (body instanceof StringBody stringBody) {
        Charset contentTypeCharset = MediaType.parse(contentTypeHeader).getCharsetOrDefault();
        Charset bodyCharset = body.getCharset(contentTypeCharset);
        return stringBody
            .getValue()
            .getBytes(
                bodyCharset != null ? bodyCharset : MediaType.DEFAULT_TEXT_HTTP_CHARACTER_SET);
      } else {
        return body.getRawBytes();
      }
    }
    return null;
  }

  public Body byteBufToBody(ByteBuf content, String contentTypeHeader) {
    if (content != null && content.readableBytes() > 0) {
      byte[] bodyBytes = new byte[content.readableBytes()];
      content.readBytes(bodyBytes);
      return bytesToBody(bodyBytes, contentTypeHeader);
    }
    return null;
  }

  public Body bytesToBody(byte[] bodyBytes, String contentTypeHeader) {
    if (bodyBytes.length > 0) {
      MediaType mediaType = MediaType.parse(contentTypeHeader);
      if (mediaType.isString()) {
        return new StringBody(
            new String(bodyBytes, mediaType.getCharsetOrDefault()),
            bodyBytes,
            isNotBlank(contentTypeHeader) ? mediaType : null);
      } else {
        return new BinaryBody(bodyBytes, mediaType);
      }
    }
    return null;
  }
}
