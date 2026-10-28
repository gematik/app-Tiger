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

import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BerTlvTest {

  @Test
  void shouldParseNestedObjectsWithMultiByteTags() {
    final List<BerTlv> tlvs =
        BerTlv.parseAll(Hex.decode("7F210A7F4E075F290170420100" + "8001FF")).orElseThrow();

    assertThat(tlvs).extracting(BerTlv::tagHex).containsExactly("7F21", "80");
    final BerTlv certificateBody = tlvs.get(0).children().get(0);
    assertThat(certificateBody.tagHex()).isEqualTo("7F4E");
    assertThat(certificateBody.children()).extracting(BerTlv::tagHex).containsExactly("5F29", "42");
    assertThat(certificateBody.children().get(0).valueOffset()).isEqualTo(9);
    assertThat(tlvs.get(1).isConstructed()).isFalse();
  }

  @Test
  void shouldParseLongFormLengths() {
    final byte[] value = new byte[0x100];
    final byte[] encoded = new byte[4 + value.length];
    System.arraycopy(Hex.decode("53820100"), 0, encoded, 0, 4);

    assertThat(BerTlv.parseAll(encoded).orElseThrow())
        .singleElement()
        .satisfies(tlv -> assertThat(tlv.valueLength()).isEqualTo(0x100))
        .satisfies(tlv -> assertThat(tlv.headerLength()).isEqualTo(4));
  }

  @Test
  void shouldParseIndefiniteLengths() {
    assertThat(
            BerTlv.parseAll(Hex.decode("3080" + "020105" + "A08004000000" + "0000")).orElseThrow())
        .singleElement()
        .satisfies(tlv -> assertThat(tlv.end()).isEqualTo(13))
        .satisfies(tlv -> assertThat(tlv.valueLength()).isEqualTo(9))
        .satisfies(
            tlv ->
                assertThat(tlv.children()).extracting(BerTlv::tagHex).containsExactly("02", "A0"));
  }

  @Test
  void shouldDistinguishUniversalTags() {
    assertThat(
            BerTlv.parseAll(Hex.decode("3000" + "A000" + "E000" + "5F2000" + "0400")).orElseThrow())
        .extracting(BerTlv::isUniversal)
        .containsExactly(true, false, false, false, true);
  }

  @Test
  void shouldPadSingleByteTags() {
    assertThat(BerTlv.parseAll(Hex.decode("0601AA")).orElseThrow())
        .extracting(BerTlv::tagHex)
        .containsExactly("06");
  }

  @ParameterizedTest
  @CsvSource({
    "''",
    "8002AA",
    "800100" + "FF",
    "0001AA",
    "80800000",
    "8401AA00",
    "A000000167455349474E",
    "2FFFFFFFFFFFFFFF",
    "7F",
    "7C038001",
    "308002010500",
    "0480AABB0000"
  })
  void shouldRejectAnythingButCompleteDataObjects(String hex) {
    assertThat(BerTlv.parseAll(Hex.decode(hex))).isEmpty();
  }
}
