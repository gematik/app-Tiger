/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Base64;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode
public class BinaryBody extends Body {
  private final byte[] bytes;

  public BinaryBody(byte[] bytes) {
    this(bytes, null);
  }

  public BinaryBody(byte[] bytes, MediaType contentType) {
    super(Type.BINARY, contentType);
    this.bytes = bytes;
  }

  public static BinaryBody binary(byte[] body) {
    return new BinaryBody(body);
  }

  public static BinaryBody binary(byte[] body, MediaType contentType) {
    return new BinaryBody(body, contentType);
  }

  public byte[] getValue() {
    return bytes;
  }

  @JsonIgnore
  public byte[] getRawBytes() {
    return bytes;
  }

  @Override
  public String toString() {
    if (bytes == null) {
      return "";
    }
    if (bytes.length > 1000) {
      return Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, 0, 1000)) + "...";
    } else {
      return Base64.getEncoder().encodeToString(bytes);
    }
  }
}
