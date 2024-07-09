/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode
public abstract class BodyWithContentType<T> extends Body<T> {
  protected final MediaType contentType;

  public BodyWithContentType(Type type, MediaType contentType) {
    super(type);
    this.contentType = contentType;
  }

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

  @Override
  @JsonIgnore
  public Charset getCharset(Charset defaultIfNotSet) {
    return determineCharacterSet(contentType, defaultIfNotSet);
  }

  @Override
  public String getContentType() {
    return (contentType != null ? contentType.toString() : null);
  }
}
