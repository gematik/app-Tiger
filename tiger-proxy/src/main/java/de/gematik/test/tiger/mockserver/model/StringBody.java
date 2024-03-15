/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import static de.gematik.test.tiger.mockserver.model.MediaType.DEFAULT_TEXT_HTTP_CHARACTER_SET;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import lombok.Getter;

/*
 * @author jamesdbloom
 */
public class StringBody extends Body {
  public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("text", "plain");
  @Getter
  private final String value;
  private byte[] rawBytes;

  public StringBody(String value) {
    this(value, null, null);
  }

  public StringBody(String value, Charset charset) {
    this(value, null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null));
  }

  public StringBody(String value, MediaType contentType) {
    this(value, null, contentType);
  }

  public StringBody(String value, byte[] rawBytes, MediaType contentType) {
    super(Type.STRING, contentType);
    this.value = isNotBlank(value) ? value : "";

    if (rawBytes == null && value != null) {
      this.rawBytes =
          value.getBytes(determineCharacterSet(contentType, DEFAULT_TEXT_HTTP_CHARACTER_SET));
    } else {
      this.rawBytes = rawBytes;
    }
  }

  @JsonIgnore
  public byte[] getRawBytes() {
    if (rawBytes == null) {
      rawBytes = value.getBytes(getCharset(DEFAULT_TEXT_HTTP_CHARACTER_SET));
    }
    return rawBytes;
  }

  @Override
  public String toString() {
    return value;
  }
}
