package de.gematik.rbellogger.facets.timing;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;

public class RbelMessageTimingPlugin extends RbelConverterPlugin {
  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PREPARATION;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    rbelElement
        .getFacet(RbelMessageMetadata.class)
        .flatMap(RbelMessageMetadata::getTransmissionTime)
        .ifPresent(
            t ->
                rbelElement.addFacet(RbelMessageTimingFacet.builder().transmissionTime(t).build()));
  }
}
