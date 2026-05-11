/*
 *
 * Copyright 2026 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.data.RbelMessageMetadata.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import lombok.val;

/**
 * Converter plugin that writes pairing metadata (the partner message's UUID) into the {@link
 * RbelMessageMetadata} during the TRANSMISSION phase, so that it can be persisted to .tgr files.
 */
public class TgrFilePairingWriterPlugin extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.TRANSMISSION;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
    if (metadataFacet.isEmpty() || rbelElement.hasFacet(RbelNonTransmissionMarkerFacet.class)) {
      return;
    }
    rbelElement
        .getFacet(TracingMessagePairFacet.class)
        .flatMap(facet -> facet.getOtherMessage(rbelElement))
        .map(RbelElement::getUuid)
        .ifPresent(uuid -> PAIRED_MESSAGE_UUID.putValue(metadataFacet.get(), uuid));
  }
}
