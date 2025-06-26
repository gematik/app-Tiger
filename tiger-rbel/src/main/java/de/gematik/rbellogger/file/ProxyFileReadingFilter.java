/*
 *
 * Copyright 2021-2025 gematik GmbH
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
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class ProxyFileReadingFilter extends RbelConverterPlugin {

  private final Map<String, RbelElement> deletedMessages = new HashMap<>();

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement message, RbelConversionExecutor converter) {
    val filterFacet = message.getFacet(TgrFileFilterFacet.class);
    val metadataFacet = message.getFacet(RbelMessageMetadata.class);
    if (filterFacet.isEmpty() || metadataFacet.isEmpty()) {
      return;
    }
    val pairedUuidOptional = PAIRED_MESSAGE_UUID.getValue(metadataFacet.get());
    if (isKeepMessage(message, filterFacet.get().filterExpression)) {
      pairedUuidOptional.ifPresent(
          partnerUuid -> {
            RbelElement deletedPartner = deletedMessages.get(partnerUuid);
            if (deletedPartner != null) {
              deletedMessages.remove(deletedPartner.getUuid());
            }
          });
    } else {
      if (pairedUuidOptional.isPresent()) {
        final var partnerMessage = deletedMessages.get(pairedUuidOptional.get());
        if (partnerMessage != null) {
          converter.removeMessage(message);
          converter.removeMessage(partnerMessage);
        }
      } else {
        deletedMessages.put(message.getUuid(), message);
      }
    }
    message.removeFacetsOfType(TgrFileFilterFacet.class);
  }

  private boolean isKeepMessage(RbelElement message, String filterExpression) {
    if (StringUtils.isEmpty(filterExpression)) {
      return true;
    }
    return TigerJexlExecutor.matchesAsJexlExpression(message, filterExpression);
  }

  /**
   * This is used to telegraph that a certain filter is to be used on this message (but has not been
   * yet)
   */
  @RequiredArgsConstructor
  public static class TgrFileFilterFacet implements RbelFacet {
    private final String filterExpression;
  }
}
