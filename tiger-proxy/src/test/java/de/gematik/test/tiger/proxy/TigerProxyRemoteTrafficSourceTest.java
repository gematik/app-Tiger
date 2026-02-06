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

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.test.tiger.proxy.controller.TrafficPushController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import kong.unirest.core.Unirest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests and demonstrates the ability to add traffic to a Tiger-Proxy via the REST-API. This is
 * useful for external traffic sources that are otherwise hard to integrate.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TigerProxyRemoteTrafficSourceTest {

  @Autowired private TigerProxy tigerProxy;

  @Test
  void sendSimpleHttpRequest() throws IOException {
    tigerProxy.clearAllMessages();
    assertThat(tigerProxy.getMessages()).isEmpty();
    Unirest.post("http://localhost:" + tigerProxy.getAdminPort() + "/traffic")
        .header(TrafficPushController.SENDER_REQUEST_HEADER, "localhost:54321")
        .header(TrafficPushController.RECEIVER_REQUEST_HEADER, "localhost:8080")
        .header(
            TrafficPushController.TIMESTAMP_REQUEST_HEADER,
            ZonedDateTime.now().minusSeconds(1).toString())
        .body(Files.readAllBytes(Path.of("src/test/resources/messages/getRequest.curl")))
        .asEmpty();
    Unirest.post("http://localhost:" + tigerProxy.getAdminPort() + "/traffic")
        .header(TrafficPushController.SENDER_REQUEST_HEADER, "localhost:8080")
        .header(TrafficPushController.RECEIVER_REQUEST_HEADER, "localhost:54321")
        .header(TrafficPushController.TIMESTAMP_REQUEST_HEADER, ZonedDateTime.now().toString())
        .body(Files.readAllBytes(Path.of("src/test/resources/messages/getResponse.curl")))
        .asEmpty();

    assertThat(tigerProxy.getMessages()).hasSize(2);
    assertThat(tigerProxy.getMessageHistory().getFirst())
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("localhost:54321");
    assertThat(tigerProxy.getMessageHistory().getFirst())
        .extractChildWithPath("$.receiver")
        .hasStringContentEqualTo("localhost:8080");
    assertThat(tigerProxy.getMessageHistory().getFirst()).hasFacet(RbelMessageTimingFacet.class);
    assertThat(tigerProxy.getMessageHistory().getLast())
        .extractChildWithPath("$.sender")
        .hasStringContentEqualTo("localhost:8080");
    assertThat(tigerProxy.getMessageHistory().getLast())
        .extractChildWithPath("$.receiver")
        .hasStringContentEqualTo("localhost:54321");
    assertThat(tigerProxy.getMessageHistory().getLast()).hasFacet(RbelMessageTimingFacet.class);
  }

  @Test
  void sendMessageWithMissingParameters_expectCorrectDefaults() throws IOException {
    tigerProxy.clearAllMessages();
    assertThat(tigerProxy.getMessages()).isEmpty();

    Unirest.post("http://localhost:" + tigerProxy.getAdminPort() + "/traffic")
        .body(Files.readAllBytes(Path.of("src/test/resources/messages/getRequest.curl")))
        .asEmpty();
    Unirest.post("http://localhost:" + tigerProxy.getAdminPort() + "/traffic")
        .body(Files.readAllBytes(Path.of("src/test/resources/messages/getResponse.curl")))
        .asEmpty();

    assertThat(tigerProxy.getMessages()).hasSize(2);
    assertThat(tigerProxy.getMessageHistory().getFirst())
        .extractChildWithPath("$.sender")
        .hasNullContent();
    assertThat(tigerProxy.getMessageHistory().getFirst())
        .extractChildWithPath("$.receiver")
        .hasNullContent();
    assertThat(tigerProxy.getMessageHistory().getFirst())
        .doesNotHaveFacet(RbelMessageTimingFacet.class);
    assertThat(tigerProxy.getMessageHistory().getLast())
        .extractChildWithPath("$.sender")
        .hasNullContent();
    assertThat(tigerProxy.getMessageHistory().getLast())
        .extractChildWithPath("$.receiver")
        .hasNullContent();
    assertThat(tigerProxy.getMessageHistory().getLast())
        .doesNotHaveFacet(RbelMessageTimingFacet.class);
  }
}
