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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Every operation of a concatenation has to give the answer of a flat content of the same bytes.
 */
class RbelContentConcatTest {

  private static final byte[] TEXT = "  hello, brave\r\nnew world  \t".getBytes(UTF_8);

  private static RbelContent reference() {
    return RbelContent.of(TEXT.clone());
  }

  private static RbelContent wrapped(int from, int to) {
    return RbelContent.wrap(Arrays.copyOfRange(TEXT, from, to));
  }

  /** Concatenations of unequal parts of every kind, all holding exactly {@link #TEXT}. */
  private static List<RbelContent> variants() {
    var variants = new ArrayList<RbelContent>();
    variants.add(
        RbelContent.concat(wrapped(0, 2), wrapped(2, 9), wrapped(9, 20), wrapped(20, TEXT.length)));
    variants.add(
        RbelContent.concat(
            IntStream.range(0, TEXT.length).mapToObj(i -> wrapped(i, i + 1)).toList()));
    variants.add(
        RbelContent.concat(
            List.of(wrapped(0, 3), wrapped(3, 3), wrapped(3, 10), wrapped(10, TEXT.length))));

    var chunkedBase = RbelContent.builder().chunkSize(4).content(List.of(TEXT.clone())).build();
    variants.add(
        RbelContent.concat(
            List.of(
                chunkedBase.subArray(0, 5),
                chunkedBase.subArray(5, 6),
                chunkedBase.subArray(6, 19),
                chunkedBase.subArray(19, TEXT.length))));

    var nested =
        RbelContent.concat(
            List.of(
                RbelContent.concat(wrapped(4, 7), wrapped(7, 12)),
                RbelContent.concat(wrapped(12, 13), wrapped(13, 22))));
    variants.add(RbelContent.concat(wrapped(0, 4), nested, wrapped(22, TEXT.length)));
    return variants;
  }

  @Test
  void readsLikeTheFlatContent() throws IOException {
    for (RbelContent concat : variants()) {
      assertThat(concat.size()).isEqualTo(TEXT.length);
      assertThat(concat.isNull()).isFalse();
      assertThat(concat.toByteArray()).isEqualTo(TEXT);
      assertThat(concat.toInputStream().readAllBytes()).isEqualTo(TEXT);
      assertThat(concat).isEqualTo(reference());
      assertThat(reference()).isEqualTo(concat);
      assertThat(concat.hashCode()).isEqualTo(reference().hashCode());
      for (int i = 0; i < TEXT.length; i++) {
        assertThat(concat.get(i)).isEqualTo(TEXT[i]);
      }
      for (int from = 0; from <= TEXT.length; from++) {
        for (int to = from; to <= TEXT.length; to++) {
          var expected = Arrays.copyOfRange(TEXT, from, to);
          assertThat(concat.toByteArray(from, to)).isEqualTo(expected);
          assertThat(concat.subArray(from, to).toByteArray()).isEqualTo(expected);
          assertThat(concat.subArray(from, to).size()).isEqualTo(to - from);
        }
      }
    }
  }

  @Test
  void searchesAcrossThePartBoundaries() {
    var expected = reference();
    for (RbelContent concat : variants()) {
      for (int start = 0; start < TEXT.length; start++) {
        for (byte probe : "lo ,\r\nwz".getBytes(UTF_8)) {
          assertThat(concat.indexOf(probe, start)).isEqualTo(expected.indexOf(probe, start));
        }
        for (int from = 0; from < TEXT.length; from++) {
          for (int length = 1; length <= 5 && from + length <= TEXT.length; length++) {
            byte[] pattern = Arrays.copyOfRange(TEXT, from, from + length);
            assertThat(concat.indexOf(pattern, start)).isEqualTo(expected.indexOf(pattern, start));
          }
        }
        assertThat(concat.indexOf("ld  \tx".getBytes(UTF_8), start)).isEqualTo(-1);
      }
      assertThat(concat.indexOf((byte) 'w')).isEqualTo(expected.indexOf((byte) 'w'));
      assertThat(concat.indexOf("brave\r\nnew".getBytes(UTF_8)))
          .isEqualTo(expected.indexOf("brave\r\nnew".getBytes(UTF_8)));
      assertThat(concat.contains("w w".getBytes(UTF_8))).isTrue();
      assertThat(concat.contains("w  w".getBytes(UTF_8))).isFalse();
    }
  }

  @Test
  void comparesPrefixesAndSuffixesAcrossThePartBoundaries() {
    var expected = reference();
    for (RbelContent concat : variants()) {
      for (int from = 0; from < TEXT.length; from++) {
        for (int length = 1; from + length <= TEXT.length; length++) {
          byte[] pattern = Arrays.copyOfRange(TEXT, from, from + length);
          for (int at = 0; at + length <= TEXT.length; at++) {
            assertThat(concat.startsWith(pattern, at)).isEqualTo(expected.startsWith(pattern, at));
          }
          assertThat(concat.startsWith(pattern)).isEqualTo(expected.startsWith(pattern));
          assertThat(concat.endsWith(pattern)).isEqualTo(expected.endsWith(pattern));
        }
      }
      for (String probe : List.of("hello", "HELLO", "he", "world", "WORLD", "ld", "xyz", "")) {
        byte[] bytes = probe.getBytes(UTF_8);
        assertThat(concat.startsTrimmedWith(bytes)).isEqualTo(expected.startsTrimmedWith(bytes));
        assertThat(concat.endsTrimmedWith(bytes)).isEqualTo(expected.endsTrimmedWith(bytes));
        assertThat(concat.startsWithIgnoreCase(bytes, UTF_8))
            .isEqualTo(expected.startsWithIgnoreCase(bytes, UTF_8));
        assertThat(concat.startsTrimmedWithIgnoreCase(bytes, UTF_8))
            .isEqualTo(expected.startsTrimmedWithIgnoreCase(bytes, UTF_8));
        assertThat(concat.endsTrimmedWithIgnoreCase(bytes, UTF_8))
            .isEqualTo(expected.endsTrimmedWithIgnoreCase(bytes, UTF_8));
      }
      assertThat(concat.startsTrimmedWith("hello".getBytes(UTF_8))).isTrue();
      assertThat(concat.endsTrimmedWithIgnoreCase("WORLD".getBytes(UTF_8), UTF_8)).isTrue();
    }
  }

  @Test
  void countsSplitsAndScansLikeTheFlatContent() {
    var expected = reference();
    for (RbelContent concat : variants()) {
      for (byte probe : "lo ,z".getBytes(UTF_8)) {
        for (int max : new int[] {1, 2, 3, 100}) {
          assertThat(concat.countOccurrencesUpTo(probe, max))
              .isEqualTo(expected.countOccurrencesUpTo(probe, max));
        }
      }
      for (String delimiter : List.of(",", "\r\n", " ", "ll", "world  \t", "nothing")) {
        assertThat(texts(concat.split(delimiter.getBytes(UTF_8))))
            .isEqualTo(texts(expected.split(delimiter.getBytes(UTF_8))));
      }
      IntPredicate[] predicates = {b -> b == 'w', b -> Character.isWhitespace(b), b -> b == 'z'};
      for (IntPredicate predicate : predicates) {
        for (int from = 0; from <= TEXT.length; from++) {
          for (int to = from; to <= TEXT.length; to++) {
            assertThat(concat.indexOfFirst(predicate, from, to))
                .isEqualTo(expected.indexOfFirst(predicate, from, to));
          }
        }
      }
    }
  }

  @Test
  void truncatingShortensTheContent() throws IOException {
    for (int usedBytes : new int[] {TEXT.length, TEXT.length - 1, 20, 10, 9, 1, 0}) {
      for (RbelContent concat : variants()) {
        concat.truncate(usedBytes);

        var expected = Arrays.copyOf(TEXT, usedBytes);
        assertThat(concat.size()).isEqualTo(usedBytes);
        assertThat(concat.toByteArray()).isEqualTo(expected);
        assertThat(concat.toInputStream().readAllBytes()).isEqualTo(expected);
        assertThat(concat.subArray(0, usedBytes).toByteArray()).isEqualTo(expected);
      }
    }
  }

  @Test
  void checksTheBounds() {
    var concat = variants().get(0);

    assertThatThrownBy(() -> concat.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> concat.get(TEXT.length)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> concat.subArray(3, 2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> concat.subArray(0, TEXT.length + 1))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> concat.truncate(TEXT.length + 1))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void aSinglePartIsNotAliasedSoTruncatingTheResultLeavesThePartAlone() {
    var part = wrapped(0, 5);

    var concat = RbelContent.concat(List.of(RbelContent.wrap(new byte[0]), part));
    concat.truncate(2);

    assertThat(concat.size()).isEqualTo(2);
    assertThat(part.size()).isEqualTo(5);
    assertThat(RbelContent.concat().size()).isZero();
    assertThat(RbelContent.concat(List.of()).size()).isZero();
  }

  @Test
  void aRangeThatCoversExactlyOnePartDoesNotHandOutTheSharedPart() {
    var first = wrapped(0, 5);
    var second = wrapped(5, 10);
    var concat = RbelContent.concat(first, second);

    for (int[] range : new int[][] {{0, 5}, {5, 10}, {1, 3}}) {
      var slice = concat.subArray(range[0], range[1]);
      slice.truncate(1);

      assertThat(slice.size()).isEqualTo(1);
    }

    assertThat(first.size()).isEqualTo(5);
    assertThat(second.size()).isEqualTo(5);
    assertThat(concat.size()).isEqualTo(10);
    assertThat(concat.toByteArray()).isEqualTo(Arrays.copyOf(TEXT, 10));
  }

  @Test
  void aConcatOfOnePartKeepsTheArrayOfThatPart() {
    var part = wrapped(0, 5);

    var concat = RbelContent.concat(part, wrapped(5, 5));
    var slice = RbelContent.concat(part, wrapped(5, 9)).subArray(0, 5);

    assertThat(concat.toByteArray()).isSameAs(part.toByteArray());
    assertThat(slice.toByteArray()).isSameAs(part.toByteArray());
  }

  @Test
  void aTruncatedConcatAnswersLikeTheFlatContentOfTheSameLength() {
    for (int usedBytes = 0; usedBytes <= TEXT.length; usedBytes++) {
      for (RbelContent concat : variants()) {
        concat.truncate(usedBytes);
        var expected = RbelContent.of(Arrays.copyOf(TEXT, usedBytes));

        assertThat(concat.size()).isEqualTo(usedBytes);
        assertThat(concat.toByteArray()).isEqualTo(expected.toByteArray());
        for (int from = 0; from <= usedBytes; from++) {
          for (int to = from; to <= usedBytes; to++) {
            assertThat(concat.subArray(from, to).toByteArray())
                .isEqualTo(expected.subArray(from, to).toByteArray());
            assertThat(concat.indexOfFirst(b -> b == 'w' || b == ',', from, to))
                .as("indexOfFirst %d..%d of %d", from, to, usedBytes)
                .isEqualTo(expected.indexOfFirst(b -> b == 'w' || b == ',', from, to));
          }
          for (byte probe : "lo ,\r\nwz".getBytes(UTF_8)) {
            assertThat(concat.indexOf(probe, from)).isEqualTo(expected.indexOf(probe, from));
          }
          assertThat(concat.indexOf("brave".getBytes(UTF_8), from))
              .isEqualTo(expected.indexOf("brave".getBytes(UTF_8), from));
        }
        for (byte probe : "lo ,\r\nwz".getBytes(UTF_8)) {
          assertThat(concat.countOccurrencesUpTo(probe, 3))
              .isEqualTo(expected.countOccurrencesUpTo(probe, 3));
        }
        for (String probe : List.of("hello", "HELLO", "world", "ld", "")) {
          byte[] bytes = probe.getBytes(UTF_8);
          assertThat(concat.startsWith(bytes)).isEqualTo(expected.startsWith(bytes));
          assertThat(concat.endsWith(bytes)).isEqualTo(expected.endsWith(bytes));
          assertThat(concat.startsTrimmedWith(bytes)).isEqualTo(expected.startsTrimmedWith(bytes));
          assertThat(concat.endsTrimmedWithIgnoreCase(bytes, UTF_8))
              .isEqualTo(expected.endsTrimmedWithIgnoreCase(bytes, UTF_8));
          assertThat(concat.contains(bytes)).isEqualTo(expected.contains(bytes));
        }
        assertThat(texts(concat.split("\r\n".getBytes(UTF_8))))
            .isEqualTo(texts(expected.split("\r\n".getBytes(UTF_8))));
      }
    }
  }

  @Test
  void neighbouringSlicesOfOneBase_areOneRunWhenTheyArePutTogether() {
    var base = reference();

    var joined = RbelContent.concat(base.subArray(2, 5), base.subArray(5, 9), base.subArray(9, 14));

    assertThat(joined).isEqualTo(base.subArray(2, 14));
    assertThat(joined.segments())
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.base()).isSameAs(base);
              assertThat(run.start()).isEqualTo(2);
              assertThat(run.size()).isEqualTo(12);
            });
  }

  @Test
  void aConcatenationHasNoConcatenationAsPart_andNoTwoPartsThatFollowEachOther() {
    var base = reference();
    var other = wrapped(0, 6);
    var nested = RbelContent.concat(base.subArray(0, 3), other);

    var all = RbelContent.concat(nested, base.subArray(3, 5), nested, base.subArray(7, 9));

    assertThat(all.segments()).hasSize(6);
    assertThat(all.segments()).allSatisfy(run -> assertThat(run.size()).isPositive());
    assertThat(all.toByteArray())
        .isEqualTo(
            concatenated(
                Arrays.copyOfRange(TEXT, 0, 3),
                Arrays.copyOfRange(TEXT, 0, 6),
                Arrays.copyOfRange(TEXT, 3, 5),
                Arrays.copyOfRange(TEXT, 0, 3),
                Arrays.copyOfRange(TEXT, 0, 6),
                Arrays.copyOfRange(TEXT, 7, 9)));
  }

  @Test
  void truncatingWhatWasJoined_doesNotReachTheSlicesItWasMadeOf() {
    var base = reference();
    var left = base.subArray(2, 5);
    var right = base.subArray(5, 9);

    var joined = RbelContent.concat(left, right);
    joined.truncate(4);

    assertThat(joined.size()).isEqualTo(4);
    assertThat(left.size()).isEqualTo(3);
    assertThat(right.size()).isEqualTo(4);
    assertThat(base.size()).isEqualTo(TEXT.length);
  }

  private static byte[] concatenated(byte[]... pieces) {
    var out = new java.io.ByteArrayOutputStream();
    for (byte[] piece : pieces) {
      out.writeBytes(piece);
    }
    return out.toByteArray();
  }

  private static List<String> texts(List<RbelContent> contents) {
    return contents.stream().map(RbelContent::toReadableString).toList();
  }
}
