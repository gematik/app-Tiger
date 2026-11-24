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
package de.gematik.rbellogger.facets.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.RbelContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.cbor.CBORMapper;

class RbelCborFastFailTest {

  private final CBORMapper mapper = new CBORMapper();

  private boolean jacksonReadsAContainer(byte[] bytes) {
    try {
      return mapper.readTree(bytes).isContainer();
    } catch (RuntimeException e) {
      return false;
    }
  }

  private boolean rejected(byte[] bytes) {
    return RbelCborConverter.isCertainlyNoCbor(RbelContent.wrap(bytes));
  }

  @Test
  void bytesThatNoCborReaderTakes_areRejected() {
    assertThat(rejected(new byte[] {(byte) 0x82, 0x01})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0xA2, 0x61, 0x61})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0x5D, 0x01})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0x98})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0x9F})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0xFF})).isTrue();
    assertThat(rejected(new byte[] {(byte) 0xDF, 0x01})).isTrue();
  }

  @Test
  void faultsDeeperThanTheHeader_areLeftToJackson() {
    assertThat(rejected(new byte[] {(byte) 0x9F, 0x5D, (byte) 0xB7})).isFalse();
    assertThat(rejected(new byte[] {(byte) 0xA1, 0x61, 0x61})).isFalse();
  }

  @Test
  void validItems_areNotRejected() {
    assertThat(rejected(new byte[] {(byte) 0xA0})).isFalse();
    assertThat(rejected(new byte[] {(byte) 0x80})).isFalse();
    assertThat(rejected(new byte[] {(byte) 0x9F, (byte) 0xFF})).isFalse();
    assertThat(rejected(new byte[] {(byte) 0xA1, 0x61, 0x61, 0x01, 0x42})).isFalse();
  }

  @Test
  void everythingTheMapperWrites_isNotRejected_andNoShortenedFormIsRejectedWhileAccepted() {
    var random = new Random(5);
    for (int i = 0; i < 1000; i++) {
      byte[] valid = mapper.writeValueAsBytes(randomValue(random, 0));
      assertThat(rejected(valid)).as("valid item %s", Arrays.toString(valid)).isFalse();
      for (int cut = 1; cut < Math.min(valid.length, 40); cut++) {
        byte[] shortened = Arrays.copyOf(valid, cut);
        if (rejected(shortened)) {
          assertThat(jacksonReadsAContainer(shortened))
              .as("shortened %s", Arrays.toString(shortened))
              .isFalse();
        }
      }
    }
  }

  @Test
  void neverRejectsWhatJacksonReads_onRandomBytes() {
    var random = new Random(7);
    int rejections = 0;
    for (int i = 0; i < 100_000; i++) {
      byte[] bytes = new byte[1 + random.nextInt(24)];
      random.nextBytes(bytes);
      bytes[0] = (byte) (0x80 + random.nextInt(0x60));
      if (rejected(bytes)) {
        rejections++;
        assertThat(jacksonReadsAContainer(bytes)).as("random %s", Arrays.toString(bytes)).isFalse();
      }
    }
    assertThat(rejections).isGreaterThan(30_000);
  }

  private Object randomValue(Random random, int depth) {
    int kind = random.nextInt(depth > 3 ? 6 : 9);
    switch (kind) {
      case 0:
        return random.nextLong();
      case 1:
        return random.nextInt(30);
      case 2:
        return "t".repeat(random.nextInt(40));
      case 3:
        return random.nextBoolean();
      case 4:
        return random.nextDouble();
      case 5:
        byte[] binary = new byte[random.nextInt(300)];
        random.nextBytes(binary);
        return binary;
      case 6, 7:
        List<Object> list = new ArrayList<>();
        for (int n = random.nextInt(30); n > 0; n--) {
          list.add(randomValue(random, depth + 1));
        }
        return list;
      default:
        Map<String, Object> map = new LinkedHashMap<>();
        for (int n = random.nextInt(30); n > 0; n--) {
          map.put("k" + n, randomValue(random, depth + 1));
        }
        return map;
    }
  }
}
