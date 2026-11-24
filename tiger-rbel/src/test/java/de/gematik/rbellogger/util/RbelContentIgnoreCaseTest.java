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
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RbelContentIgnoreCaseTest {

  private static final byte[] ALPHABET = {
    'B',
    'e',
    'a',
    'r',
    'E',
    'R',
    'b',
    'A',
    'S',
    'i',
    'c',
    ' ',
    ':',
    '1',
    (byte) 0xC3,
    (byte) 0xA4,
    (byte) 0xE2,
    (byte) 0x84,
    (byte) 0xAA,
    (byte) 0xFF,
    0
  };

  private static boolean reference(byte[] content, byte[] prefix, Charset charset) {
    return prefix.length <= content.length
        && new String(Arrays.copyOf(content, prefix.length), charset)
            .equalsIgnoreCase(new String(prefix, charset));
  }

  @Test
  void startsWithIgnoreCase_givesTheAnswerOfTheStringComparison() {
    var random = new Random(3);
    for (Charset charset : List.of(UTF_8, ISO_8859_1, US_ASCII, UTF_16)) {
      for (int i = 0; i < 40_000; i++) {
        byte[] content = randomBytes(random, 1 + random.nextInt(12));
        byte[] prefix =
            i % 3 == 0 ? randomBytes(random, 1 + random.nextInt(6)) : asciiPrefix(random, content);
        assertThat(RbelContent.of(content).startsWithIgnoreCase(prefix, charset))
            .as(
                "%s starts with %s in %s",
                Arrays.toString(content), Arrays.toString(prefix), charset)
            .isEqualTo(reference(content, prefix, charset));
      }
    }
  }

  private static byte[] randomBytes(Random random, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return result;
  }

  private static byte[] asciiPrefix(Random random, byte[] content) {
    byte[] prefix = Arrays.copyOf(content, 1 + random.nextInt(Math.min(content.length, 6)));
    for (int i = 0; i < prefix.length; i++) {
      if (prefix[i] > 0 && random.nextBoolean()) {
        prefix[i] ^= 0x20;
      }
    }
    return prefix;
  }
}
