/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import javax.annotation.Nullable;
import lombok.Builder;

@Builder
public record RbelMimeMultipartFacet(
    @Nullable RbelElement preamble, RbelElement parts, @Nullable RbelElement epilogue)
    implements RbelFacet {

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .withSkipIfNull("preamble", preamble)
        .with("parts", parts)
        .withSkipIfNull("epilogue", epilogue);
  }
}
