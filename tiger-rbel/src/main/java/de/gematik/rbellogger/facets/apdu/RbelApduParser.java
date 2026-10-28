/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.apdu;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.facets.apdu.ApduCommandDictionary.Description;
import de.gematik.rbellogger.facets.asn1.RbelAsn1DataObjectsFacet;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;

/**
 * Builds the Rbel representation of command and response APDUs. Protocols transporting APDUs
 * (SICCT, ...) call this on the element holding exactly one APDU. Data fields are marked as BER
 * encoded, so that the ASN.1 parser structures them if possible.
 */
public final class RbelApduParser {

  private RbelApduParser() {}

  public static Optional<RbelApduCommandFacet> parseCommand(
      RbelElement element, ApduCommandDictionary dictionary, RbelConversionExecutor context) {
    return CommandApdu.parse(element.getRawContent())
        .map(
            apdu -> {
              final RbelApduCommandFacet facet =
                  buildCommandFacet(element, apdu, dictionary.describe(apdu));
              element.addFacet(facet);
              Optional.ofNullable(facet.getData()).ifPresent(data -> convertData(data, context));
              return facet;
            });
  }

  public static Optional<RbelApduResponseFacet> parseResponse(
      RbelElement element, RbelConversionExecutor context) {
    final int size = (int) element.getSize();
    if (size < 2) {
      return Optional.empty();
    }
    final RbelElement sw = bytes(element, size - 2, size);
    final byte[] swBytes = sw.getRawContent();
    final int swValue = (Byte.toUnsignedInt(swBytes[0]) << 8) | Byte.toUnsignedInt(swBytes[1]);
    sw.addFacet(new RbelApduStatusWordFacet(RbelElement.wrap(sw, StatusWord.describe(swValue))));
    final RbelElement data = size == 2 ? null : slice(element, 0, size - 2);
    final RbelApduResponseFacet facet = new RbelApduResponseFacet(data, sw);
    element.addFacet(facet);
    Optional.ofNullable(data).ifPresent(d -> convertData(d, context));
    return Optional.of(facet);
  }

  private static RbelApduCommandFacet buildCommandFacet(
      RbelElement element, CommandApdu apdu, Optional<Description> description) {
    final RbelElement cla = bytes(element, 0, 1);
    CommandClass.decode(apdu.getCla())
        .ifPresent(commandClass -> cla.addFacet(RbelApduClassFacet.of(cla, commandClass)));
    final int leEnd = apdu.getLeOffset() + apdu.getLeLength();
    return RbelApduCommandFacet.builder()
        .cla(cla)
        .ins(bytes(element, 1, 2))
        .p1(bytes(element, 2, 3))
        .p2(bytes(element, 3, 4))
        .lc(
            apdu.getLcLength() == 0
                ? null
                : number(element, CommandApdu.HEADER_LENGTH, apdu.getDataOffset(), apdu.getNc()))
        .data(apdu.getNc() == 0 ? null : slice(element, apdu.getDataOffset(), apdu.getLeOffset()))
        .le(
            apdu.getLeLength() == 0
                ? null
                : number(element, apdu.getLeOffset(), leEnd, apdu.getNe()))
        .apduCase(RbelElement.wrap(element, apdu.getApduCase().label()))
        .name(description.map(d -> RbelElement.wrap(element, d.name())).orElse(null))
        .description(description.orElse(null))
        .build();
  }

  private static void convertData(RbelElement data, RbelConversionExecutor context) {
    data.getParentNode().addFacet(new RbelAsn1DataObjectsFacet());
    context.convertElement(data);
  }

  private static RbelElement slice(RbelElement element, int from, int to) {
    return RbelElement.create(element.getContent().subArray(from, to), element);
  }

  private static RbelElement bytes(RbelElement element, int from, int to) {
    final RbelElement result = slice(element, from, to);
    return result.addFacet(RbelValueFacet.of(hex(result.getRawContent())));
  }

  private static String hex(byte[] bytes) {
    return Hex.toHexString(bytes).toUpperCase();
  }

  private static RbelElement number(RbelElement element, int from, int to, int value) {
    return slice(element, from, to).addFacet(RbelValueFacet.of(value));
  }
}
