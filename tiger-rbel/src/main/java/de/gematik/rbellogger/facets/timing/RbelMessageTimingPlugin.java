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
