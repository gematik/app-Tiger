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
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MockServerToRbelConverterTest {

  private final MockServerToRbelConverter converter =
      new MockServerToRbelConverter(RbelLogger.build().getRbelConverter());

  @Test
  void theBodyOfARequestIsNotCopiedIntoTheMessage() {
    byte[] body = new byte[100_000];
    Arrays.fill(body, (byte) 7);
    var request =
        HttpRequest.request("/upload")
            .setMethod("POST")
            .withHeader("Host", "server")
            .withBody(body);
    byte[] head = "POST /upload HTTP/1.1\r\nHost: server\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    var content = converter.requestToRbelMessage(request).getContent();

    assertThat(content.size()).isEqualTo(head.length + body.length);
    assertThat(content.toByteArray(0, head.length)).isEqualTo(head);
    assertThat(content.subArray(head.length, content.size()).toByteArray()).isSameAs(body);
  }

  @Test
  void theBodyOfAResponseIsNotCopiedIntoTheMessage() {
    byte[] body = new byte[100_000];
    Arrays.fill(body, (byte) 9);
    var response =
        HttpResponse.response().withStatusCode(200).withReasonPhrase("OK").withBody(body);
    byte[] head = "HTTP/1.1 200 OK\r\n\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    var content = converter.responseToRbelMessage(response, null).getContent();

    assertThat(content.size()).isEqualTo(head.length + body.length);
    assertThat(content.toByteArray(0, head.length)).isEqualTo(head);
    assertThat(content.subArray(head.length, content.size()).toByteArray()).isSameAs(body);
  }

  @Test
  void aMessageWithoutBodyIsConverted() {
    var response = HttpResponse.response().withStatusCode(204);
    var request = HttpRequest.request("/nothing").setMethod("GET");

    assertThat(converter.responseToRbelMessage(response, null).getContent().toReadableString())
        .isEqualTo("HTTP/1.1 204 \r\n\r\n\r\n");
    assertThat(converter.requestToRbelMessage(request).getContent().toReadableString())
        .isEqualTo("GET /nothing HTTP/1.1\r\n\r\n\r\n");
  }
}
