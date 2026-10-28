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
package de.gematik.rbellogger.facets.asn1;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.asn1.RbelAsn1TreePrinter.Line;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelAsn1TreePrinterTest {

  private static final String CARD_VERIFIABLE_CERTIFICATE =
      "7f2181d87f4e81915f290170420844454758588702227f494d06082a8648ce3d04030286410428405a0ccc5c53"
          + "b6780356a5141eb47fed5f56be44bc22f2046fc053fedbc25e50e24a6d6af95c1cfee9497acce359a253f7"
          + "d0b7abaea5d1a62de030145f0c975f200844454758581102237f4c1306082a8214004c0481185307800000"
          + "000000005f25060203000703015f24060301000703005f37404cd260c0803b125a001ba81ba9f2e2b1390d"
          + "e4f14691c822a28cc776a186d7ba7f08704c27fdcdaeb1f8b243a37976cf37bf7c121858d0f0419de83217"
          + "a395de";

  private static RbelElement convertDataObjects(String hex) {
    final RbelElement parent = new RbelElement(new byte[0], null);
    parent.addFacet(new RbelAsn1DataObjectsFacet());
    return RbelLogger.build(new RbelConfiguration().activateConversionFor("asn1"))
        .getRbelConverter()
        .convertElement(new RbelElement(Hex.decode(hex), parent));
  }

  @Test
  void shouldPrintOneLinePerDataObject() {
    assertThat(RbelAsn1TreePrinter.print(convertDataObjects(CARD_VERIFIABLE_CERTIFICATE)))
        .startsWith(
            new Line(0, "7F21", "[APPLICATION 33]", ""),
            new Line(1, "7F4E", "[APPLICATION 78]", ""),
            new Line(2, "5F29", "[APPLICATION 41]", "70"),
            new Line(2, "42", "[APPLICATION 2]", "4445475858870222"),
            new Line(2, "7F49", "[APPLICATION 73]", ""),
            new Line(3, "0", "OBJECT IDENTIFIER", "1.2.840.10045.4.3.2 (ecdsaWithSHA256)"),
            new Line(
                3, "86", "[CONTEXT 6]", "0428405a0ccc5c53b6780356a5141eb4…145f0c97 (65 bytes)"));
  }

  @Test
  void shouldPrintSequencesWithUniversalTypesAndTextValues() {
    final RbelElement sequence =
        RbelLogger.build(new RbelConfiguration().activateConversionFor("asn1"))
            .getRbelConverter()
            .convertElement(
                Hex.decode(
                    "300F" + "020105" + "130244 45".replace(" ", "") + "8606" + "687474703A2F"),
                null);

    assertThat(RbelAsn1TreePrinter.print(sequence))
        .containsExactly(
            new Line(0, "", "SEQUENCE", ""),
            new Line(1, "0", "INTEGER", "5"),
            new Line(1, "1", "PrintableString", "DE"),
            new Line(1, "86", "[CONTEXT 6]", "\"http:/\""));
  }

  @ParameterizedTest
  @CsvSource({
    "30, SEQUENCE",
    "A0, [CONTEXT 0]",
    "E0, [PRIVATE 0]",
    "5F20, [APPLICATION 32]",
    "7F21, [APPLICATION 33]",
    "9F8101, [CONTEXT 129]"
  })
  void shouldDescribeTags(String tag, String description) {
    assertThat(RbelAsn1TreePrinter.describeTag(tag)).isEqualTo(description);
  }
}
