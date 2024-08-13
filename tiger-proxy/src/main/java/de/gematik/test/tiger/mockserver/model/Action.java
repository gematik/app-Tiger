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
import lombok.Data;

/*
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Data
public abstract class Action<T extends Action> extends ObjectWithJsonToString {
  private String expectationId;

  @JsonIgnore
  public abstract Type getType();

  public enum Type {
    FORWARD(Direction.FORWARD),
    FORWARD_TEMPLATE(Direction.FORWARD),
    FORWARD_CLASS_CALLBACK(Direction.FORWARD),
    FORWARD_OBJECT_CALLBACK(Direction.FORWARD),
    FORWARD_REPLACE(Direction.FORWARD),
    RESPONSE(Direction.RESPONSE),
    RESPONSE_TEMPLATE(Direction.RESPONSE),
    RESPONSE_CLASS_CALLBACK(Direction.RESPONSE),
    RESPONSE_OBJECT_CALLBACK(Direction.RESPONSE),
    ERROR(Direction.RESPONSE);

    public final Direction direction;

    Type(Direction direction) {
      this.direction = direction;
    }
  }

  public enum Direction {
    FORWARD,
    RESPONSE
  }
}
