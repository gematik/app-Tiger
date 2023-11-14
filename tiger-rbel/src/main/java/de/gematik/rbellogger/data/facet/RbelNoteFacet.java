/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class RbelNoteFacet implements RbelFacet {
  private final String value;
  private final NoteStyling style;

  public RbelNoteFacet(String value) {
    this.value = value;
    this.style = NoteStyling.INFO;
  }

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap();
  }

  @RequiredArgsConstructor
  public enum NoteStyling {
    INFO("text-info"),
    WARN("has-text-warning"),
    ERROR("has-text-danger");

    private final String cssClass;

    public String toCssClass() {
      return cssClass;
    }
  }
}
