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
package de.gematik.rbellogger.facets.sicct;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@ConverterInfo(onlyActivateFor = "sicct")
@Slf4j
public class RbelSicctEnvelopeConverter extends RbelConverterPlugin {

  private static final int ENVELOPE_LENGTH = 10;

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    if (element.getParentNode() != null
        || element.hasFacet(RbelHttpMessageFacet.class)
        || element.getSize() < 11) {
      return;
    }
    try {
      final RbelSicctEnvelopeFacet envelopeFacet = buildEnvelopeFacet(element);
      element.addFacet(envelopeFacet);
      context.convertElement(envelopeFacet.getCommand());
      envelopeFacet
          .getMessageType()
          .seekValue(SicctMessageType.class)
          .ifPresent(
              msgType -> {
                if (msgType == SicctMessageType.C_COMMAND) {
                  element
                      .findMessage()
                      .addFacet(new RbelRequestFacet(requestInfoString(envelopeFacet), false));
                } else {
                  element
                      .findMessage()
                      .addFacet(
                          new RbelResponseFacet(
                              Hex.toHexString(
                                  element
                                      .getContent()
                                      .toByteArray(
                                          (int) element.getSize() - 2, (int) element.getSize()))));
                }
              });
    } catch (RuntimeException e) {
      // swallow
    }
  }

  private String requestInfoString(RbelSicctEnvelopeFacet envelopeFacet) {
    return envelopeFacet
        .getCommand()
        .getFacet(RbelSicctCommandFacet.class)
        .map(RbelSicctCommandFacet::getHeader)
        .flatMap(el -> el.getFacet(RbelSicctHeaderFacet.class))
        .map(RbelSicctHeaderFacet::getCommand)
        .map(Objects::toString)
        .orElse("");
  }

  private RbelSicctEnvelopeFacet buildEnvelopeFacet(RbelElement element) {
    // compare SICCT-specification, chapter 6.1.4.2
    RbelContent content = element.getContent();
    final byte[] lengthContent = content.toByteArray(6, 10);
    int length = ByteBuffer.wrap(lengthContent).getInt();
    final RbelElement commandElement =
        new RbelElement(
            null,
            content.subArray(ENVELOPE_LENGTH, length + ENVELOPE_LENGTH),
            element,
            Optional.empty());
    element.setUsedBytes(length + ENVELOPE_LENGTH);
    commandElement.addFacet(new RbelBinaryFacet());
    return RbelSicctEnvelopeFacet.builder()
        .messageType(
            RbelElement.wrap(
                new byte[] {content.get(0)}, element, SicctMessageType.of(content.get(0))))
        .srcOrDesAddress(new RbelElement(content.toByteArray(1, 3), element))
        .sequenceNumber(new RbelElement(content.toByteArray(3, 5), element))
        .abRfu(new RbelElement(content.toByteArray(5, 6), element))
        .length(new RbelElement(content.toByteArray(6, 10), element))
        .command(commandElement)
        .build();
  }
}
