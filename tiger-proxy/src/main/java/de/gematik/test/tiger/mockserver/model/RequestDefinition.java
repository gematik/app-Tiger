/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/*
 * @author jamesdbloom
 */
public abstract class RequestDefinition extends Not {

  private String logCorrelationId;

  @JsonIgnore
  public String getLogCorrelationId() {
    return logCorrelationId;
  }

  public RequestDefinition withLogCorrelationId(String logCorrelationId) {
    this.logCorrelationId = logCorrelationId;
    return this;
  }

  public abstract RequestDefinition shallowClone();

  public RequestDefinition cloneWithLogCorrelationId() {
    return shallowClone().withLogCorrelationId(getLogCorrelationId());
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
