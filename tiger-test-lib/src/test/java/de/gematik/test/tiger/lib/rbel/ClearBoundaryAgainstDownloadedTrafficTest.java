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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.test.tiger.proxy.data.TigerDownloadedMessageFacet;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClearBoundaryAgainstDownloadedTrafficTest {

  private LocalProxyRbelMessageListenerTestAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new LocalProxyRbelMessageListenerTestAdapter();
  }

  @Test
  @DisplayName("Traffic from before the clear stays invisible when it is downloaded afterwards")
  void downloadedTrafficPredatingTheClear_isNotValidatable() {
    adapter.addMessage(liveMessage(1, ZonedDateTime.now().minusMinutes(5)));
    adapter.getLocalProxyRbelMessageListener().clearValidatableRbelMessages();

    adapter.addMessage(downloadedMessage(2, ZonedDateTime.now().minusMinutes(4)));

    assertThat(validatableMessages())
        .as(
            "the message was transmitted during the previous scenario and only recorded now,"
                + " because the catch-up download reached it after the clear")
        .isEmpty();
  }

  @Test
  @DisplayName("Traffic downloaded after the clear but transmitted after it stays validatable")
  void downloadedTrafficFromAfterTheClear_isStillValidatable() {
    adapter.addMessage(liveMessage(1, ZonedDateTime.now().minusMinutes(5)));
    adapter.getLocalProxyRbelMessageListener().clearValidatableRbelMessages();

    val caughtUp = downloadedMessage(2, ZonedDateTime.now().plusSeconds(1));
    adapter.addMessage(caughtUp);

    assertThat(validatableMessages())
        .as("a reconnect must not lose traffic that belongs to the running scenario")
        .containsExactly(caughtUp);
  }

  @Test
  @DisplayName("A live message is never filtered out, whatever its transmission time says")
  void liveTrafficIsNeverFilteredByTheBoundary() {
    adapter.addMessage(liveMessage(1, ZonedDateTime.now().minusMinutes(5)));
    adapter.getLocalProxyRbelMessageListener().clearValidatableRbelMessages();

    val inFlightDuringTheClear = liveMessage(2, ZonedDateTime.now().minusMinutes(4));
    adapter.addMessage(inFlightDuringTheClear);

    assertThat(validatableMessages())
        .as("a message recorded live after the clear was transmitted after it, by construction")
        .containsExactly(inFlightDuringTheClear);
  }

  private Iterable<RbelElement> validatableMessages() {
    return adapter.getLocalProxyRbelMessageListener().getValidatableMessages().getMessages();
  }

  private RbelElement liveMessage(long sequenceNumber, ZonedDateTime transmissionTime) {
    val element = new RbelElement("msg".getBytes(StandardCharsets.UTF_8), null);
    element.setSequenceNumber(sequenceNumber);
    element.addFacet(RbelMessageTimingFacet.builder().transmissionTime(transmissionTime).build());
    // an unparsed message is cut off by getLongestFinishedMessagesPrefix before any filter runs
    element.setConversionPhase(RbelConversionPhase.COMPLETED);
    return element;
  }

  private RbelElement downloadedMessage(long sequenceNumber, ZonedDateTime transmissionTime) {
    val element = liveMessage(sequenceNumber, transmissionTime);
    element.addFacet(new TigerDownloadedMessageFacet());
    return element;
  }
}
