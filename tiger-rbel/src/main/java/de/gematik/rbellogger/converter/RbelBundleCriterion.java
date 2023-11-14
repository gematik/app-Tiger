/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Builder(access = AccessLevel.PUBLIC)
@Getter
public class RbelBundleCriterion {

  private final String bundledServerName;
  private final List<String> sender;
  private final List<String> receiver;
}
