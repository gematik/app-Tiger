/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelJexlShadingExpression {

  private final String jexlExpression;
  private final String shadingValue;
  private final AtomicInteger numberOfMatches = new AtomicInteger(0);
}
