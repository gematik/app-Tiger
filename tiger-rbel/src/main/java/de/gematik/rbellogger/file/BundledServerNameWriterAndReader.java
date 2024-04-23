/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.file;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
public class BundledServerNameWriterAndReader
    implements RbelFilePreSaveListener, RbelMessagePostProcessor {

  private static final String BUNDLED_HOSTNAME_CLIENT = "bundledHostnameClient";
  private static final String BUNDLED_HOSTNAME_SERVER = "bundledHostnameServer";

  @Override
  public void preSaveCallback(RbelElement rbelElement, JSONObject messageObject) {
    messageObject.put(
        BUNDLED_HOSTNAME_CLIENT,
        extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getSender));
    messageObject.put(
        BUNDLED_HOSTNAME_SERVER,
        extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getReceiver));
  }

  @Override
  public void performMessagePostConversionProcessing(
      RbelElement message, RbelConverter converter, JSONObject messageObject) {
    performBundledServerNameExtraction(
        message, messageObject, BUNDLED_HOSTNAME_CLIENT, RbelTcpIpMessageFacet::getSender);
    performBundledServerNameExtraction(
        message, messageObject, BUNDLED_HOSTNAME_SERVER, RbelTcpIpMessageFacet::getReceiver);
  }

  private static void performBundledServerNameExtraction(
      RbelElement message,
      JSONObject messageObject,
      String jsonKey,
      Function<RbelTcpIpMessageFacet, RbelElement> targetRecipient) {
    if (messageObject.has(jsonKey)) {
      final String bundledServername = messageObject.getString(jsonKey);
      message
          .getFacet(RbelTcpIpMessageFacet.class)
          .map(targetRecipient)
          .ifPresent(
              recipient -> {
                RbelHostnameFacet oldFacet =
                    recipient.getFacet(RbelHostnameFacet.class).orElse(null);
                if (oldFacet != null) {
                  recipient.addOrReplaceFacet(
                      RbelHostnameFacet.builder()
                          .domain(oldFacet.getDomain())
                          .port(oldFacet.getPort())
                          .bundledServerName(
                              Optional.of(RbelElement.wrap(recipient, bundledServername)))
                          .build());
                }
              });
    }
  }

  private static String extractBundledHostname(
      RbelElement rbelElement, Function<RbelTcpIpMessageFacet, RbelElement> targetRecipient) {
    return rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(targetRecipient)
        .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
        .flatMap(RbelHostnameFacet::getBundledServerName)
        .map(RbelElement::getRawStringContent)
        .orElse(null);
  }
}
