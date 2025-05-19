package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(addAutomatically = false)
@Slf4j
public class TigerProxyRemoteTransmissionConversionPlugin extends RbelConverterPlugin {
  @Getter private final AbstractTigerProxy tigerProxy;

  public TigerProxyRemoteTransmissionConversionPlugin(AbstractTigerProxy tigerProxy) {
    this.tigerProxy = tigerProxy;
  }

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.TRANSMISSION;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
    if (metadataFacet.isPresent() && !rbelElement.hasFacet(RbelNonTransmissionMarkerFacet.class)) {
      tigerProxy.triggerListener(rbelElement, metadataFacet.get());
    }
  }
}
