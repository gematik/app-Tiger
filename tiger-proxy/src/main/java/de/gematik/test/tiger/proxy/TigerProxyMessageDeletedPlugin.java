package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;

@EqualsAndHashCode(callSuper = true)
@ConverterInfo(addAutomatically = false)
@Value
public class TigerProxyMessageDeletedPlugin extends RbelConverterPlugin {
  TigerRemoteProxyClient tigerProxy;

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.DELETION;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
    if (metadataFacet.isPresent() && !rbelElement.hasFacet(RbelNonTransmissionMarkerFacet.class)) {
      tigerProxy.signalNewCompletedMessage(rbelElement);
    }
  }
}
