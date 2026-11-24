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
package de.gematik.rbellogger.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Base64AlphabetTest {

  @Test
  void theUrlSafeValuesAreThoseOfTheJdkDecoder() {
    for (int b = -128; b < 128; b++) {
      int expected = jdkUrlValue((byte) b);
      assertThat(Base64Alphabet.urlSafeValue((byte) b))
          .as("value of byte %d", b)
          .isEqualTo(expected);
    }
  }

  @Test
  void bothAlphabetsAreCharactersAndNothingElseIs() {
    for (int b = -128; b < 256; b++) {
      boolean expected = jdkUrlValue((byte) b) >= 0 || b == '+' || b == '/';
      assertThat(Base64Alphabet.isBase64Character(b))
          .as("character %d", b)
          .isEqualTo(b < 128 && expected);
    }
    assertThat(Base64Alphabet.isBase64Character('=')).isFalse();
  }

  private static final byte[] SAMPLE_BYTES = {
    'A',
    'Q',
    'a',
    'z',
    '0',
    '9',
    '+',
    '/',
    '-',
    '_',
    '=',
    '=',
    ' ',
    '\n',
    (byte) 0xC3,
    (byte) 0xA9,
    0
  };

  private static final List<Base64.Decoder> DECODERS =
      List.of(Base64.getDecoder(), Base64.getUrlDecoder());

  private static byte[] randomBytes(Random random) {
    byte[] result = new byte[random.nextInt(11)];
    for (int i = 0; i < result.length; i++) {
      result[i] = SAMPLE_BYTES[random.nextInt(SAMPLE_BYTES.length)];
    }
    return result;
  }

  @Test
  void whatAStrictDecoderDecodesCanBeBase64() {
    var random = new Random(11);
    for (int i = 0; i < 200_000; i++) {
      byte[] bytes = randomBytes(random);
      String text = new String(bytes, ISO_8859_1);
      assertThat(Base64Alphabet.canBeBase64(text))
          .as("text and content variants for %s", text.replace("\n", "<LF>"))
          .isEqualTo(Base64Alphabet.canBeBase64(RbelContent.wrap(bytes)));
      for (Base64.Decoder decoder : DECODERS) {
        if (decodes(() -> decoder.decode(bytes))) {
          assertThat(Base64Alphabet.canBeBase64(RbelContent.wrap(bytes)))
              .as("strictly decoded %s", text.replace("\n", "<LF>"))
              .isTrue();
        }
      }
    }
  }

  private static byte[] viaStream(Base64.Decoder decoder, byte[] bytes) {
    try {
      return RbelContent.from(decoder.wrap(new ByteArrayInputStream(bytes))).toByteArray();
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  @Test
  void theChosenStreamDecoderGivesTheAnswerOfEveryStreamDecoderThatDecodes() {
    var random = new Random(12);
    for (int i = 0; i < 200_000; i++) {
      byte[] bytes = randomBytes(random);
      String shown = new String(bytes, ISO_8859_1).replace("\n", "<LF>");
      Base64.Decoder chosen = Base64Alphabet.streamDecoderFor(RbelContent.wrap(bytes));
      for (Base64.Decoder decoder : DECODERS) {
        byte[] answer = viaStream(decoder, bytes);
        if (answer != null) {
          assertThat(chosen).as("a decoder for %s", shown).isNotNull();
          assertThat(viaStream(chosen, bytes)).as("answer for %s", shown).isEqualTo(answer);
        }
      }
    }
  }

  @Test
  void theDecoderIsChosenByTheCharactersOfTheAlphabetItDoesNotShare() {
    assertThat(streamDecoderFor("abcd")).isSameAs(Base64.getDecoder());
    assertThat(streamDecoderFor("ab+/")).isSameAs(Base64.getDecoder());
    assertThat(streamDecoderFor("ab-_")).isSameAs(Base64.getUrlDecoder());
    assertThat(streamDecoderFor("ab+-")).isNull();
    assertThat(streamDecoderFor("hello world!")).isNull();
    assertThat(streamDecoderFor("AAAAA")).isNull();
  }

  @Test
  void aStreamDecoderIgnoresWhatFollowsThePadding() {
    assertThat(viaStream(Base64.getDecoder(), "YQ==\n".getBytes(ISO_8859_1))).isNotNull();
    assertThat(streamDecoderFor("YQ==\n")).isSameAs(Base64.getDecoder());
    assertThat(Base64Alphabet.canBeBase64(RbelContent.wrap("YQ==\n".getBytes(ISO_8859_1))))
        .isFalse();
  }

  @Test
  void onlyTheCharactersBeforeThePaddingChooseTheDecoder() {
    assertThat(streamDecoderFor("YQ==-_")).isSameAs(Base64.getDecoder());
    assertThat(streamDecoderFor("YQ==+/")).isSameAs(Base64.getDecoder());
  }

  private static Base64.Decoder streamDecoderFor(String text) {
    return Base64Alphabet.streamDecoderFor(RbelContent.wrap(text.getBytes(ISO_8859_1)));
  }

  private interface Decoding {
    Object run() throws IOException;
  }

  private static boolean decodes(Decoding decoding) {
    try {
      decoding.run();
      return true;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  private static int jdkUrlValue(byte b) {
    if (b < 0 || b == '=') {
      return -1;
    }
    try {
      // with three 'A' (value 0) before it, the third decoded byte is the value of the character
      return Base64.getUrlDecoder().decode(new byte[] {'A', 'A', 'A', b})[2] & 0xFF;
    } catch (IllegalArgumentException e) {
      return -1;
    }
  }
}
