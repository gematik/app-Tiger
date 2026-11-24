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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelContentTest {

  @Test
  void wrapReturnsTheSameArrayEvenAboveTheDefaultChunkSize() {
    byte[] small = "0123456789".getBytes();
    byte[] large = new byte[20_000];
    Arrays.fill(large, (byte) 7);

    assertSame(small, RbelContent.wrap(small).toByteArray());
    assertSame(large, RbelContent.wrap(large).toByteArray());
  }

  @Test
  void append() {
    RbelContentBase bytes = RbelContent.builder().chunkSize(10).build();
    byte[] input = "0123456789AB".getBytes();
    byte[] firstChunk = Arrays.copyOfRange(input, 0, 3);
    byte[] secondChunk = Arrays.copyOfRange(input, 3, 6);
    byte[] thirdChunk = Arrays.copyOfRange(input, 6, 11);
    byte[] fourthChunk = Arrays.copyOfRange(input, 11, 12);

    bytes.append(
        RbelContent.builder()
            .chunkSize(10)
            .content(List.of(firstChunk, secondChunk, thirdChunk))
            .build());

    assertEquals(11, bytes.size());
    assertNotNull(bytes.getChunks());
    assertEquals(2, bytes.getChunks().size());
    assertEquals(10, bytes.getChunks().get(0).length);
    assertEquals(1, bytes.getChunks().get(1).length);
    assertArrayEquals(Arrays.copyOfRange(input, 0, 10), bytes.getChunks().get(0));
    assertArrayEquals(Arrays.copyOfRange(input, 10, 11), bytes.getChunks().get(1));
    assertArrayEquals(bytes.toByteArray(0, 3), firstChunk);
    assertArrayEquals(bytes.toByteArray(3, 6), secondChunk);
    assertArrayEquals(bytes.toByteArray(6, 11), thirdChunk);

    bytes.append(fourthChunk);
    assertEquals(10, bytes.getChunks().get(1).length);
    assertArrayEquals(bytes.toByteArray(11, 12), fourthChunk);
  }

  @Test
  void indexOfFirst_findsAByteAcrossChunksAndInASliceWithoutCopying() {
    RbelContentBase bytes = RbelContent.builder().chunkSize(4).build();
    bytes.append("abcdefghij=lmn".getBytes());

    assertEquals(10, bytes.indexOfFirst(b -> b == '=', 0, bytes.size()));
    assertEquals(-1, bytes.indexOfFirst(b -> b == '=', 0, 10));
    assertEquals(-1, bytes.indexOfFirst(b -> b == 'z', 0, bytes.size()));
    assertEquals(7, bytes.indexOfFirst(b -> b == 'h', 3, bytes.size()));

    RbelContent slice = bytes.subArray(5, 12);
    assertEquals("fghij=l", new String(slice.toByteArray()));
    assertEquals(5, slice.indexOfFirst(b -> b == '=', 0, slice.size()));
    assertEquals(2, slice.indexOfFirst(b -> b == 'h', 1, slice.size()));
    assertEquals(-1, slice.indexOfFirst(b -> b == 'm', 0, slice.size()));
  }

  @Test
  void appendShort() {
    RbelContentBase bytes = RbelContent.builder().chunkSize(10).build();
    byte[] input = "0123456789AB".getBytes();
    byte[] firstChunk = Arrays.copyOfRange(input, 0, 3);
    byte[] secondChunk = Arrays.copyOfRange(input, 3, 6);

    bytes.append(firstChunk);
    bytes.append(secondChunk);

    assertEquals(6, bytes.size());
    assertNotNull(bytes.getChunks());
    assertEquals(1, bytes.getChunks().size());
    assertEquals(10, bytes.getChunks().get(0).length);
    assertArrayEquals(bytes.toByteArray(0, 3), firstChunk);
    assertArrayEquals(bytes.toByteArray(3, 6), secondChunk);
  }

  @Test
  void inputStream() throws IOException {
    RbelContentBase bytes = RbelContent.builder().chunkSize(10).build();
    byte[] input = "0123456789AB".getBytes();
    byte[] firstChunk = Arrays.copyOfRange(input, 0, 3);
    byte[] secondChunk = Arrays.copyOfRange(input, 3, 6);

    bytes.append(firstChunk);
    bytes.append(secondChunk);

    try (var inputStream = bytes.toInputStream()) {
      var inputStreamBytes = inputStream.readAllBytes();
      assertEquals(bytes.size(), inputStreamBytes.length);
      assertArrayEquals(bytes.toByteArray(0, 6), inputStreamBytes);
    }
  }

  @Test
  void isNull() {
    RbelContentBase bytes = RbelContent.builder().chunkSize(10).build();
    assertTrue(bytes.isNull());
    assertTrue(bytes.isEmpty());
    assertEquals(0, bytes.size());

    bytes.append(new byte[10]);
    assertFalse(bytes.isNull());
    assertFalse(bytes.isEmpty());
    assertEquals(10, bytes.size());
  }

  @Test
  void toInputStream() throws IOException {
    RbelContentBase bytes = RbelContent.builder().chunkSize(10).build();
    bytes.append("foobar foobar foobar".getBytes());
    int start = 3;
    int end = 12;
    try (var inputStream = bytes.toInputStream(start, end)) {
      var inputStreamBytes = inputStream.readAllBytes();
      assertEquals(end - start, inputStreamBytes.length);
      assertArrayEquals(bytes.toByteArray(start, end), inputStreamBytes);
    }

    var slice = bytes.subArray(start, end);
    try (var inputStream = slice.toInputStream()) {
      var inputStreamBytes = inputStream.readAllBytes();
      assertEquals(end - start, inputStreamBytes.length);
      assertArrayEquals(slice.toByteArray(), inputStreamBytes);
    }
  }

  @Test
  void toInputStreamOfEveryRangeContainsExactlyTheBytesOfTheRange() throws IOException {
    byte[] input = new byte[47];
    for (int i = 0; i < input.length; i++) {
      input[i] = (byte) i;
    }
    for (int chunkSize : new int[] {1, 7, 10, 47, 100}) {
      RbelContentBase content =
          RbelContent.builder().chunkSize(chunkSize).content(List.of(input)).build();
      for (int start = 0; start <= input.length; start++) {
        for (int end = start; end <= input.length; end++) {
          try (var inputStream = content.toInputStream(start, end)) {
            assertArrayEquals(
                Arrays.copyOfRange(input, start, end),
                inputStream.readAllBytes(),
                "chunk size " + chunkSize + ", range [" + start + ", " + end + ")");
          }
        }
      }
    }
  }

  @Test
  void toInputStreamOfAnEmptyContentIsEmpty() throws IOException {
    try (var inputStream = RbelContent.of(new byte[0]).toInputStream()) {
      assertEquals(0, inputStream.readAllBytes().length);
    }
    try (var inputStream = RbelContent.builder().build().toInputStream()) {
      assertEquals(0, inputStream.readAllBytes().length);
    }
  }

  @Test
  void streamingManySmallSlicesOfALargeContentDoesNotReadTheBytesInFrontOfEachSlice() {
    // e.g. the lines of a large e-mail body: if every stream first read all the bytes before its
    // slice, this would read about 6 TB and take minutes instead of a few seconds
    final int sliceSize = 80;
    final int numberOfSlices = 400_000;
    byte[] input = new byte[sliceSize * numberOfSlices];
    for (int i = 0; i < input.length; i++) {
      input[i] = (byte) (i % 251);
    }
    var content = RbelContent.of(input);

    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          for (int i = 0; i < numberOfSlices; i++) {
            var slice = content.subArray(i * sliceSize, (i + 1) * sliceSize);
            try (var inputStream = slice.toInputStream()) {
              byte[] read = inputStream.readAllBytes();
              assertEquals(sliceSize, read.length);
              assertEquals(input[i * sliceSize], read[0]);
              assertEquals(input[(i + 1) * sliceSize - 1], read[sliceSize - 1]);
            }
          }
        });
  }

  @Test
  void subArray() {
    var bytes =
        RbelContent.builder()
            .chunkSize(10)
            .content(List.of("foobar foobar foobar".getBytes()))
            .build();
    var concat =
        RbelContent.of(List.of(bytes.subArray(0, 3), bytes.subArray(3, 7), bytes.subArray(7, 20)));
    assertEquals(concat.size(), bytes.size());
    assertArrayEquals(concat.toByteArray(), bytes.toByteArray());
    assertArrayEquals(bytes.subArray(3, 7).toByteArray(), bytes.toByteArray(3, 7));
    assertEquals(
        bytes.subArray(3, 15).indexOf("foobar".getBytes()),
        bytes.indexOf("foobar".getBytes(), 3) - 3);
  }

  @Test
  void split() {
    var bytes =
        RbelContent.builder()
            .chunkSize(10)
            .content(List.of("foobar foobar foobar".getBytes()))
            .build();
    byte[] SPACE = " ".getBytes();
    var parts = bytes.split(SPACE);
    assertEquals(3, parts.size());
    for (int i = 0; i < parts.size() - 1; i++) {
      assertTrue(parts.get(i).endsWith(SPACE));
    }
    var concat = RbelContent.of(parts);
    assertEquals(concat.size(), bytes.size());
    assertArrayEquals(concat.toByteArray(), bytes.toByteArray());
  }
}
