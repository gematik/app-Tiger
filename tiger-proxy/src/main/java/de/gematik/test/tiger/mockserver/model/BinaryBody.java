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

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Base64;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
public class BinaryBody extends BodyWithContentType<byte[]> {
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

  @Override
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
