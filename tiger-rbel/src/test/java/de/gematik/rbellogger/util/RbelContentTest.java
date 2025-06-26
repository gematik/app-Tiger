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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelContentTest {

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

  @Test
  @SneakyThrows
  @Tag("de.gematik.test.tiger.common.PerformanceTest")
  void compareChunkedAgainstNonChunked_chunkedShouldBeSimilar() {
    var origContent =
        Files.readAllBytes(Paths.get("src/test/resources/sampleMessages/sampleMail_longer.txt"));
    RbelContentBase chunked = RbelContent.builder().content(List.of(origContent)).build();

    double ratios = 0.0;

    int cycles = 20;

    for (int i = 0; i < cycles; i++) {
      System.gc();

      RbelLogger logger =
          RbelLogger.build(
              RbelConfiguration.builder().activateRbelParsingFor(List.of("mime", "smtp")).build());

      byte[] concatenated = new byte[0];
      var begin1 = System.nanoTime();
      for (byte[] chunk : chunked.getChunks()) {
        concatenated = org.bouncycastle.util.Arrays.concatenate(concatenated, chunk);
        var rawContent = // simulate a non-chunked input by making the whole input a single chunk
            RbelContent.builder()
                .chunkSize(concatenated.length)
                .content(List.of(concatenated))
                .build();
        logger.getRbelConverter().convertElement(RbelElement.builder().content(rawContent).build());
      }
      var end1 = System.nanoTime();

      log.info("Concatenating and converting content took {} ns", (end1 - begin1));
      var concatenatedNs = (end1 - begin1);

      RbelContentBase array = RbelContent.builder().build();

      var begin2 = System.nanoTime();
      for (byte[] chunk : chunked.getChunks()) {
        array.append(chunk);
        logger.getRbelConverter().convertElement(RbelElement.builder().content(array).build());
      }
      var end2 = System.nanoTime();

      log.info("Accumulating and converting took {} ns", (end2 - begin2));

      var accumulatedNs = (end2 - begin2);

      double performanceRatio = ((double) accumulatedNs) / ((double) concatenatedNs);
      log.info("performance ratio accumulated/concatenated == {}", performanceRatio);

      ratios += performanceRatio;
    }

    double averagePerformanceRatio = ratios / cycles;
    log.info("average performance ratio accumulated/concatenated == {}", averagePerformanceRatio);
    org.assertj.core.api.Assertions.assertThat(averagePerformanceRatio).isLessThan(5.0F);
  }
}
