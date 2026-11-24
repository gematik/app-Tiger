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
package de.gematik.test.tiger.proxy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompHeaders;

class BinaryTracingFrameTest {

  private static StompHeaders asReceived(Map<String, Object> headers) {
    StompHeaders received = new StompHeaders();
    headers.forEach((name, value) -> received.add(name, value.toString()));
    return received;
  }

  @Test
  void aFrame_isThePieceOfAMessage() {
    var headers = BinaryTracingFrame.headers("11111111-2222-3333-4444-555555555555", 2, 5);

    var part = BinaryTracingFrame.part(asReceived(headers), new byte[] {1, 2, 3});

    assertThat(part.getUuid()).isEqualTo("11111111-2222-3333-4444-555555555555");
    assertThat(part.getIndex()).isEqualTo(2);
    assertThat(part.getNumberOfMessages()).isEqualTo(5);
    assertThat(part.getData()).containsExactly(1, 2, 3);
  }

  @Test
  void aMessageOfOneFrame_hasNoPartHeaders() {
    var headers = BinaryTracingFrame.headers("uuid", 0, 1);

    assertThat(headers).containsOnlyKeys(BinaryTracingFrame.HEADER_MESSAGE_UUID);
    var part = BinaryTracingFrame.part(asReceived(headers), new byte[0]);
    assertThat(part.getIndex()).isZero();
    assertThat(part.getNumberOfMessages()).isEqualTo(1);
  }

  @Test
  void aFrameOfAnotherPiece_hasTheHeaders() {
    assertThat(BinaryTracingFrame.headers("uuid", 2, 5))
        .containsOnlyKeys(
            BinaryTracingFrame.HEADER_MESSAGE_UUID,
            BinaryTracingFrame.HEADER_PART_INDEX,
            BinaryTracingFrame.HEADER_PART_COUNT);
  }

  @Test
  void aFrameWithoutItsHeaders_isRejected() {
    assertThatThrownBy(() -> BinaryTracingFrame.part(new StompHeaders(), new byte[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BinaryTracingFrame.HEADER_MESSAGE_UUID);
  }
}
