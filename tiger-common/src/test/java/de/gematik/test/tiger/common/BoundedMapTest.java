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
package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoundedMapTest {

  private BoundedMap<String, String> boundedMap;

  @BeforeEach
  void setUp() {
    boundedMap = new BoundedMap<>(3); // Create a bounded map with a max size of 3
  }

  @Test
  void testAddAndRetrieve() {
    boundedMap.getOrPutDefault("key1", () -> "value1");
    Optional<String> value = boundedMap.get("key1");
    assertThat(value).contains("value1");
  }

  @Test
  void testRemove() {
    boundedMap.getOrPutDefault("key1", () -> "value1");
    boundedMap.remove("key1");
    assertThat(boundedMap.get("key1")).isEmpty();
  }

  @Test
  void testEvictionWhenMaxSizeExceeded() {
    boundedMap.getOrPutDefault("key1", () -> "value1");
    boundedMap.getOrPutDefault("key2", () -> "value2");
    boundedMap.getOrPutDefault("key3", () -> "value3");
    boundedMap.getOrPutDefault("key4", () -> "value4"); // This should evict "key1"

    assertThat(boundedMap.get("key1")).isEmpty();
    assertThat(boundedMap.get("key2")).contains("value2");
    assertThat(boundedMap.get("key3")).contains("value3");
    assertThat(boundedMap.get("key4")).contains("value4");
  }

  @Test
  void testGetOrPutDefault() {
    String value = boundedMap.getOrPutDefault("key1", () -> "defaultValue");
    assertThat(value).isEqualTo("defaultValue");

    assertThat(boundedMap.get("key1")).hasValue("defaultValue");
  }

  @Test
  void testEntries() {
    boundedMap.getOrPutDefault("key1", () -> "value1");
    boundedMap.getOrPutDefault("key2", () -> "value2");

    List<Map.Entry<String, String>> entries = boundedMap.entries();
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0).getKey()).isEqualTo("key1");
    assertThat(entries.get(0).getValue()).isEqualTo("value1");
    assertThat(entries.get(1).getKey()).isEqualTo("key2");
    assertThat(entries.get(1).getValue()).isEqualTo("value2");
  }

  @Test
  void testSize() {
    assertThat(boundedMap.size()).isZero();
    boundedMap.getOrPutDefault("key1", () -> "value1");
    assertThat(boundedMap.size()).isOne();
    boundedMap.getOrPutDefault("key2", () -> "value2");
    assertThat(boundedMap.size()).isEqualTo(2);
  }

  @Test
  void testIsThreadSafe() throws InterruptedException {
    BoundedMap<Integer, Integer> threadSafeMap = new BoundedMap<>(100);
    Runnable task =
        () -> {
          for (int i = 0; i < 1000; i++) {
            val f = i;
            threadSafeMap.getOrPutDefault(i, () -> f);
          }
        };

    Thread thread1 = new Thread(task);
    Thread thread2 = new Thread(task);

    thread1.start();
    thread2.start();
    thread1.join();
    thread2.join();

    assertThat(threadSafeMap.size()).isEqualTo(100); // Ensure bounded size is respected
  }
}
