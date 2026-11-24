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
package de.gematik.rbellogger.facets.pki.base64;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.Base64Alphabet;
import de.gematik.rbellogger.util.RbelContent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The decoding of small content must give the answer of the stream decoder, errors included, for
 * all the content that the converter passes on to be decoded.
 */
class Base64DecodingTest {

  private static final List<Base64.Decoder> DECODERS =
      List.of(Base64.getDecoder(), Base64.getUrlDecoder());

  private static byte[] viaStream(Base64.Decoder decoder, byte[] input) {
    try {
      return RbelContent.from(decoder.wrap(new ByteArrayInputStream(input))).toByteArray();
    } catch (Exception e) {
      return null;
    }
  }

  private static byte[] viaHelper(Base64.Decoder decoder, byte[] input) {
    try {
      return RbelBase64JsonConverter.decode(decoder, RbelContent.wrap(input)).toByteArray();
    } catch (Exception e) {
      return null;
    }
  }

  private static void assertSameAnswer(String input) {
    byte[] bytes = input.getBytes(US_ASCII);
    if (!Base64Alphabet.canBeBase64(RbelContent.wrap(bytes))) {
      return;
    }
    for (Base64.Decoder decoder : DECODERS) {
      assertThat(Arrays.equals(viaHelper(decoder, bytes), viaStream(decoder, bytes)))
          .as("decoding '%s'", input.replace("\n", "\n"))
          .isTrue();
    }
  }

  @Test
  void edgeCasesGiveTheSameAnswerAsTheStreamDecoder() {
    for (String input :
        List.of(
            "",
            "Y",
            "YQ",
            "YQ=",
            "YQ==",
            "YQ===",
            "YQ====",
            "YWI",
            "YWI=",
            "YWJj",
            "e30",
            "e30=",
            "eyJhIjoxfQ",
            "eyJhIjoxfQ==",
            "ab-_",
            "ab+/",
            "ab-/",
            "ab+_",
            "====",
            "=YQ=",
            "YQ=Q",
            "YQ\n==",
            "YQ ==",
            " YQ==",
            "YQ==\n",
            "\n",
            " ",
            "A",
            "AA",
            "AAA",
            "AAAA",
            "AAAAA")) {
      assertSameAnswer(input);
    }
  }

  @Test
  void randomInputsGiveTheSameAnswerAsTheStreamDecoder() {
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/-_";
    var random = new Random(4711);
    for (int i = 0; i < 3000; i++) {
      var input = new StringBuilder();
      int length = random.nextInt(41);
      for (int j = 0; j < length; j++) {
        input.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }
      input.append("=".repeat(random.nextInt(3)));
      assertSameAnswer(input.toString());
    }
  }

  @Test
  void validContentOfEverySizeIsDecodedAlsoAboveTheLimitForSmallContent() throws IOException {
    var random = new Random(1);
    for (int size : new int[] {0, 1, 2, 3, 100, 6143, 6144, 6145, 8192, 20_000}) {
      byte[] original = new byte[size];
      random.nextBytes(original);
      byte[] encoded = Base64.getEncoder().encode(original);

      byte[] decoded =
          RbelBase64JsonConverter.decode(Base64.getDecoder(), RbelContent.wrap(encoded))
              .toByteArray();

      assertThat(decoded).isEqualTo(original);
    }
  }
}
