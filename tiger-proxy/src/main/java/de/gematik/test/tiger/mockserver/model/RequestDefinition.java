/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

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
    return MockServerLogger.isEnabled(Level.TRACE) && isNotBlank(getLogCorrelationId())
        ? shallowClone().withLogCorrelationId(getLogCorrelationId())
        : this;
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
