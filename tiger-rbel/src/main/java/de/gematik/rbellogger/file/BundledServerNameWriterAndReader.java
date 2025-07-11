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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMessageMetadata.RbelMetadataValue;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BundledServerNameWriterAndReader {

  public static final RbelMetadataValue<String> BUNDLED_HOSTNAME_SENDER =
      new RbelMetadataValue<>("bundledHostnameSender", String.class);
  public static final RbelMetadataValue<String> BUNDLED_HOSTNAME_RECEIVER =
      new RbelMetadataValue<>("bundledHostnameReceiver", String.class);

  public static class BundledServerNameReader extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.PROTOCOL_PARSING;
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      rbelElement
          .getFacet(RbelMessageMetadata.class)
          .ifPresent(
              metadata -> {
                BUNDLED_HOSTNAME_SENDER
                    .getValue(metadata)
                    .ifPresent(
                        data ->
                            performBundledServerNameExtraction(
                                rbelElement, RbelTcpIpMessageFacet::getSender, data));
                BUNDLED_HOSTNAME_RECEIVER
                    .getValue(metadata)
                    .ifPresent(
                        data ->
                            performBundledServerNameExtraction(
                                rbelElement, RbelTcpIpMessageFacet::getReceiver, data));
              });
    }

    private static void performBundledServerNameExtraction(
        RbelElement message,
        Function<RbelTcpIpMessageFacet, RbelElement> targetExtractor,
        Object extractedValue) {
      message
          .getFacet(RbelTcpIpMessageFacet.class)
          .map(targetExtractor)
          .ifPresent(
              target -> {
                RbelHostnameFacet oldFacet = target.getFacet(RbelHostnameFacet.class).orElse(null);
                if (oldFacet != null) {
                  target.addOrReplaceFacet(
                      RbelHostnameFacet.builder()
                          .domain(oldFacet.getDomain())
                          .port(oldFacet.getPort())
                          .bundledServerName(Optional.of(RbelElement.wrap(target, extractedValue)))
                          .build());
                }
              });
    }
  }

  public static class BundledServerNameWriter extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      rbelElement
          .getFacet(RbelMessageMetadata.class)
          .ifPresent(
              metadata -> {
                BUNDLED_HOSTNAME_SENDER.putValue(
                    metadata,
                    extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getSender));
                BUNDLED_HOSTNAME_RECEIVER.putValue(
                    metadata,
                    extractBundledHostname(rbelElement, RbelTcpIpMessageFacet::getReceiver));
              });
    }

    private static String extractBundledHostname(
        RbelElement rbelElement, Function<RbelTcpIpMessageFacet, RbelElement> targetExtractor) {
      return rbelElement
          .getFacet(RbelTcpIpMessageFacet.class)
          .map(targetExtractor)
          .flatMap(el -> el.getFacet(RbelHostnameFacet.class))
          .flatMap(RbelHostnameFacet::getBundledServerName)
          .map(RbelElement::getRawStringContent)
          .orElse(null);
    }
  }
}
