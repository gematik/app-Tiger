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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
public abstract class Body<T> extends ObjectWithJsonToString {
  private final Type type;

  Body(Type type) {
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  public abstract T getValue();

  @JsonIgnore
  public byte[] getRawBytes() {
    return toString().getBytes(UTF_8);
  }

  @JsonIgnore
  public Charset getCharset(Charset defaultIfNotSet) {
    if (this instanceof BodyWithContentType) {
      return this.getCharset(defaultIfNotSet);
    }
    return defaultIfNotSet;
  }

  public String getContentType() {
    if (this instanceof BodyWithContentType) {
      return this.getContentType();
    }
    return null;
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
