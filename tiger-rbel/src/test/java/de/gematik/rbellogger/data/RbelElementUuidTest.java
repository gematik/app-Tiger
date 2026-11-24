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
package de.gematik.rbellogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RbelElementUuidTest {

  private static RbelElement element(String uuid) {
    return new RbelElement(uuid, new byte[] {1}, null, Optional.empty());
  }

  @Test
  void aGivenUuid_isKept() {
    assertThat(element("given").getUuid()).isEqualTo("given");
    assertThat(element("given").toBuilder().build().getUuid()).isEqualTo("given");
  }

  @Test
  void anElementWithoutUuid_getsOneThatStaysTheSame_andThatDiffersFromOthers() {
    var first = element(null);
    var second = element("");

    assertThat(first.getUuid()).isNotBlank().isEqualTo(first.getUuid());
    assertThat(first.getUuid()).isNotEqualTo(second.getUuid());
    assertThat(first.toBuilder().build().getUuid()).isEqualTo(first.getUuid());
  }

  @Test
  void severalThreadsAskingForTheUuid_getTheSameOne() throws Exception {
    var element = element(null);
    var executor = Executors.newFixedThreadPool(8);
    try {
      List<String> uuids =
          executor
              .invokeAll(
                  IntStream.range(0, 64)
                      .<java.util.concurrent.Callable<String>>mapToObj(i -> element::getUuid)
                      .toList())
              .stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                      throw new IllegalStateException(e);
                    }
                  })
              .collect(Collectors.toList());
      assertThat(Set.copyOf(uuids)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }
}
