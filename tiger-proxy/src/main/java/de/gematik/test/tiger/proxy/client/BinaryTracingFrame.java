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

import java.util.HashMap;
import java.util.Map;
import org.springframework.messaging.simp.stomp.StompHeaders;

/**
 * A frame with a piece of the data of a message of a Tiger proxy to its remotes, see ADR 026: the
 * body is the bytes, without JSON around them and without base64, and the STOMP headers say which
 * piece of which message it is. The metadata of the message is not in the frames, it comes on the
 * destination that every Tiger proxy has.
 */
public final class BinaryTracingFrame {

  // The names are short, as they are in every frame. A header that has its usual value is left out:
  // most messages are one frame.
  public static final String HEADER_MESSAGE_UUID = "uuid";

  /** The index of the piece, left out for the first. */
  public static final String HEADER_PART_INDEX = "part";

  /** The number of pieces, left out if it is one. */
  public static final String HEADER_PART_COUNT = "parts";

  private BinaryTracingFrame() {}

  /** The headers of a frame. */
  public static Map<String, Object> headers(String messageUuid, int partIndex, int partCount) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(HEADER_MESSAGE_UUID, messageUuid);
    if (partIndex != 0) {
      headers.put(HEADER_PART_INDEX, Integer.toString(partIndex));
    }
    if (partCount != 1) {
      headers.put(HEADER_PART_COUNT, Integer.toString(partCount));
    }
    return headers;
  }

  /** The piece of the message that is in the body. */
  public static TracingMessagePart part(StompHeaders headers, byte[] body) {
    return TracingMessagePart.builder()
        .uuid(required(headers, HEADER_MESSAGE_UUID))
        .index(intOrDefault(headers, HEADER_PART_INDEX, 0))
        .numberOfMessages(intOrDefault(headers, HEADER_PART_COUNT, 1))
        .data(body)
        .build();
  }

  private static int intOrDefault(StompHeaders headers, String name, int defaultValue) {
    String value = headers.getFirst(name);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  private static String required(StompHeaders headers, String name) {
    String value = headers.getFirst(name);
    if (value == null) {
      throw new IllegalArgumentException("A frame of a message has no header " + name);
    }
    return value;
  }
}
