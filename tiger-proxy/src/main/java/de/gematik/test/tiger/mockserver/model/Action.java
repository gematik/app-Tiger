/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
