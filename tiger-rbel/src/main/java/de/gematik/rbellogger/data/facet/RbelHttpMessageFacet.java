/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class RbelHttpMessageFacet implements RbelFacet {

  private final RbelElement header;
  private final RbelElement body;
  private final RbelElement httpVersion;

  @Builder(toBuilder = true)
  public RbelHttpMessageFacet(RbelElement header, RbelElement body, RbelElement httpVersion) {
    this.header = header;
    this.body = body;
    this.httpVersion = httpVersion;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("body", body)
        .with("header", header)
        .with("httpVersion", httpVersion);
  }
}
