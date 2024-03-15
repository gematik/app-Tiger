/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import lombok.Data;

/*
 * @author jamesdbloom
 */
@Data
public abstract class Body {

  private final Type type;
  private final MediaType contentType;

  @JsonIgnore
  public abstract byte[] getRawBytes();

  @JsonIgnore
  Charset determineCharacterSet(MediaType mediaType, Charset defaultCharset) {
    if (mediaType != null) {
      Charset charset = mediaType.getCharset();
      if (charset != null) {
        return charset;
      }
    }
    return defaultCharset;
  }

  @JsonIgnore
  public Charset getCharset(Charset defaultIfNotSet) {
    return determineCharacterSet(contentType, defaultIfNotSet);
  }

  public String getContentType() {
    return (contentType != null ? contentType.toString() : null);
  }

  public enum Type {
    BINARY,
    JSON,
    JSON_SCHEMA,
    JSON_PATH,
    PARAMETERS,
    REGEX,
    STRING,
    XML,
    XML_SCHEMA,
    XPATH,
    LOG_EVENT,
  }
}
