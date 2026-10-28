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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.facets.apdu.CommandApdu.ApduCase;
import de.gematik.rbellogger.facets.apdu.CommandClass.SecureMessaging;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandApduTest {

  @ParameterizedTest
  @CsvSource({
    "00A4000C,          CASE_1,  0, 0, 0, 0",
    "00B0000000,        CASE_2S, 0, 0, 1, 256",
    "00B0000010,        CASE_2S, 0, 0, 1, 16",
    "00A4040C02AABB,    CASE_3S, 1, 2, 0, 0",
    "002A9E9A02AABB00,  CASE_4S, 1, 2, 1, 256",
    "00B00000000000,    CASE_2E, 0, 0, 3, 65536",
    "00B0000000012C,    CASE_2E, 0, 0, 3, 300",
    "00D60000000002AABB,CASE_3E, 3, 2, 0, 0",
    "002A9E9A000002AABB0100, CASE_4E, 3, 2, 2, 256"
  })
  void shouldDistinguishTheCasesOfIso7816(
      String hex, ApduCase apduCase, int lcLength, int nc, int leLength, int ne) {
    final CommandApdu apdu = CommandApdu.parse(Hex.decode(hex)).orElseThrow();

    assertThat(apdu.getApduCase()).isEqualTo(apduCase);
    assertThat(apdu.getLcLength()).isEqualTo(lcLength);
    assertThat(apdu.getNc()).isEqualTo(nc);
    assertThat(apdu.getLeLength()).isEqualTo(leLength);
    assertThat(apdu.getNe()).isEqualTo(ne);
    assertThat(apdu.getData()).hasSize(nc);
  }

  @ParameterizedTest
  @CsvSource({"00A400", "00A4040C02AA", "00A4040C02AABBCCDD", "00D600000000", "00D6000000000002AA"})
  void shouldRejectInconsistentLengths(String hex) {
    assertThat(CommandApdu.parse(Hex.decode(hex))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "00, false, NONE,                     0",
    "10, true,  NONE,                     0",
    "0C, false, HEADER_AUTHENTICATED,     0",
    "08, false, HEADER_NOT_AUTHENTICATED, 0",
    "03, false, NONE,                     3",
    "40, false, NONE,                     4",
    "7F, true,  HEADER_NOT_AUTHENTICATED, 19"
  })
  void shouldDecodeInterindustryClasses(
      String cla, boolean chaining, SecureMessaging secureMessaging, int logicalChannel) {
    assertThat(CommandClass.decode(Integer.parseInt(cla, 16)))
        .contains(new CommandClass(chaining, secureMessaging, logicalChannel));
  }

  @ParameterizedTest
  @CsvSource({"80", "8C", "20", "FF"})
  void shouldNotDecodeProprietaryOrReservedClasses(String cla) {
    assertThat(CommandClass.decode(Integer.parseInt(cla, 16))).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "9000, Normal processing",
    "6A82, File or application not found",
    "63C2, 'Verification failed, 2 retries left'",
    "6110, 16 further response bytes available",
    "6C20, 'Wrong Le, 32 bytes available'",
    "6299, 'Warning, state of non-volatile memory unchanged'",
    "1234, Unknown status word"
  })
  void shouldDescribeStatusWords(String sw, String meaning) {
    assertThat(StatusWord.describe(Integer.parseInt(sw, 16))).isEqualTo(meaning);
  }
}
