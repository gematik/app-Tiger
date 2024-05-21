/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class RbelNestedFacet implements RbelFacet {

  private final RbelElement nestedElement;
  private final String nestedElementName;

  public RbelNestedFacet(RbelElement nestedElement) {
    this.nestedElement = nestedElement;
    this.nestedElementName = "content";
  }

  @Builder
  public RbelNestedFacet(RbelElement nestedElement, String nestedElementName) {
    this.nestedElement = nestedElement;
    if (StringUtils.isNotEmpty(nestedElementName)) {
      this.nestedElementName = nestedElementName;
    } else {
      this.nestedElementName = "content";
    }
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with(nestedElementName, nestedElement);
  }
}
