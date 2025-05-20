/*
 *
 * Copyright 2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
