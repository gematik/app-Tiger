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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BinaryClassifierTest {

  @Test
  void plainAsciiText_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary("hello world".getBytes())).isFalse();
    assertThat(BinaryClassifier.isBinary("hello world\n".getBytes())).isFalse();
  }

  @Test
  void textWithTabCrLf_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary("line1\r\nline2\ttab".getBytes())).isFalse();
  }

  @Test
  void validUtf8WithUmlauts_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary("Ärzte und Übung".getBytes())).isFalse();
  }

  @Test
  void validUtf8WithEmoji_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary("hello 🌍".getBytes())).isFalse();
  }

  @Test
  void controlCharacters_shouldBeBinary() {
    assertThat(BinaryClassifier.isBinary(new byte[] {0x00, 0x01})).isTrue();
    assertThat(BinaryClassifier.isBinary(new byte[] {0x11, 0x7F})).isTrue();
  }

  @Test
  void invalidUtf8Sequences_shouldBeBinary() {
    // 0x82 alone is not a valid UTF-8 start byte
    assertThat(BinaryClassifier.isBinary(new byte[] {(byte) 0x82, (byte) 0x86})).isTrue();
    // 0xFF is never valid in UTF-8
    assertThat(BinaryClassifier.isBinary(new byte[] {(byte) 0xFF})).isTrue();
    // Truncated 2-byte sequence
    assertThat(BinaryClassifier.isBinary(new byte[] {(byte) 0xC0})).isTrue();
  }

  @Test
  void emptyArray_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary(new byte[0])).isFalse();
  }

  @Test
  void hpackPayload_shouldBeBinary() {
    // Typical HPACK-compressed HTTP/2 header payload
    assertThat(BinaryClassifier.isBinary(new byte[] {(byte) 0x82, (byte) 0x86, (byte) 0x84}))
        .isTrue();
  }

  @Test
  void jsonText_shouldNotBeBinary() {
    assertThat(BinaryClassifier.isBinary("{\"key\":\"value\"}".getBytes())).isFalse();
  }

  @Test
  void incompleteSequenceAtTheEnd_shouldBeBinary() {
    byte[] data = {'a', 'b', 'c', (byte) 0xE2, (byte) 0x82};

    assertThat(BinaryClassifier.isBinary(data)).isTrue();
    assertThat(BinaryClassifier.isBinary(new java.io.ByteArrayInputStream(data))).isTrue();
  }

  @Test
  void onlyTheFirst512Characters_areLookedAt() {
    byte[] data = new byte[600];
    java.util.Arrays.fill(data, (byte) 'a');
    data[550] = 0;

    assertThat(BinaryClassifier.isBinary(data)).isFalse();
    assertThat(BinaryClassifier.isBinary(new java.io.ByteArrayInputStream(data))).isFalse();
    data[500] = 0;
    assertThat(BinaryClassifier.isBinary(data)).isTrue();
    assertThat(BinaryClassifier.isBinary(new java.io.ByteArrayInputStream(data))).isTrue();
  }

  @Test
  void content_isClassifiedLikeTheBytes_alsoInSeveralChunksAndViews() {
    byte[][] samples = {
      "plain text".getBytes(java.nio.charset.StandardCharsets.UTF_8),
      "grüße € 日本語 🌍".getBytes(java.nio.charset.StandardCharsets.UTF_8),
      {'a', 'b', 0, 'c'},
      {'a', 'b', (byte) 0xE2, (byte) 0x82},
      {(byte) 0xC3, 0x28},
      new byte[700]
    };
    java.util.Arrays.fill(samples[5], (byte) 'x');
    samples[5][600] = 1;

    for (byte[] sample : samples) {
      for (int split = 0; split <= sample.length; split++) {
        var chunks =
            RbelContent.of(
                java.util.List.of(
                    RbelContent.of(java.util.Arrays.copyOfRange(sample, 0, split)),
                    RbelContent.of(java.util.Arrays.copyOfRange(sample, split, sample.length))));
        assertThat(BinaryClassifier.isBinary(chunks))
            .as("%s split at %d", java.util.Arrays.toString(sample), split)
            .isEqualTo(BinaryClassifier.isBinary(sample));
      }
    }
  }

  @Test
  void aViewOntoALargerContent_isClassifiedLikeItsBytes() {
    byte[][] samples = {
      "plain text".getBytes(java.nio.charset.StandardCharsets.UTF_8),
      "grüße € 日本語".getBytes(java.nio.charset.StandardCharsets.UTF_8),
      {'a', 0, 'b'},
      {(byte) 0xC3, 0x28}
    };

    for (byte[] sample : samples) {
      byte[] outside =
          "A\u0000\u0001ÿ outside \u0000".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
      byte[] all = new byte[outside.length * 2 + sample.length];
      System.arraycopy(outside, 0, all, 0, outside.length);
      System.arraycopy(sample, 0, all, outside.length, sample.length);
      System.arraycopy(outside, 0, all, outside.length + sample.length, outside.length);

      var view = RbelContent.of(all).subArray(outside.length, outside.length + sample.length);

      assertThat(BinaryClassifier.isBinary(view))
          .as(java.util.Arrays.toString(sample))
          .isEqualTo(BinaryClassifier.isBinary(sample));
    }
  }

  @Test
  void controlCharacterAfterAMultibyteCharacter_shouldBeBinary() {
    byte[] data = "ä".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    data = java.util.Arrays.copyOf(data, data.length + 3);
    data[2] = 'a';
    data[3] = 3;
    data[4] = 'b';

    assertThat(BinaryClassifier.isBinary(data)).isTrue();
    assertThat(BinaryClassifier.isBinary(new java.io.ByteArrayInputStream(data))).isTrue();
  }
}
