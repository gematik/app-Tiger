package de.gematik.test.tiger.mockserver.model;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import java.util.Objects;

public abstract class Body<T> extends Not {
  private int hashCode;
  private final Type type;
  private Boolean optional;

  public Body(Type type) {
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  public Boolean getOptional() {
    return optional;
  }

  public Body<T> withOptional(Boolean optional) {
    this.optional = optional;
    return this;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (hashCode() != o.hashCode()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    Body<?> body = (Body<?>) o;
    return type == body.type && Objects.equals(optional, body.optional);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Objects.hash(super.hashCode(), type, optional);
    }
    return hashCode;
  }
}
