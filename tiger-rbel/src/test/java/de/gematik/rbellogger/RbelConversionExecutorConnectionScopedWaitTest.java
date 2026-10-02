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
package de.gematik.rbellogger;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelSocketAddressFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * A same-connection lookup (TLS read/write, message pairing, websocket handshake, ...) can never be
 * answered by a message on a different TCP connection, whether or not that message has finished
 * parsing. Waiting for it anyway - which the connection-agnostic
 * waitForAllElementsBeforeGivenToBeParsed used to do - lets a slow or stuck connection stall
 * unrelated connections for no reason.
 */
class RbelConversionExecutorConnectionScopedWaitTest {

  private static final RbelSocketAddress CONNECTION_A_CLIENT =
      RbelSocketAddress.fromString("clientA:80").orElseThrow();
  private static final RbelSocketAddress CONNECTION_A_SERVER =
      RbelSocketAddress.fromString("serverA:80").orElseThrow();
  private static final RbelSocketAddress CONNECTION_B_CLIENT =
      RbelSocketAddress.fromString("clientB:80").orElseThrow();
  private static final RbelSocketAddress CONNECTION_B_SERVER =
      RbelSocketAddress.fromString("serverB:80").orElseThrow();

  @Test
  void aStuckMessageOnOneConnectionDoesNotBlockASameConnectionLookupOnAnother() {
    var converter =
        RbelLogger.build(RbelConfiguration.builder().parsingTimeoutInSeconds(1).build())
            .getRbelConverter();
    addStuckPredecessor(converter, CONNECTION_A_CLIENT, CONNECTION_A_SERVER);

    var result = new AtomicReference<Optional<RbelElement>>();
    converter.addConverter(
        RbelConverterPlugin.createPlugin(
            (element, executor) ->
                result.set(executor.findPreviousMessageInSameConnectionAs(element))));

    var secondMessage = new RbelElement("second message".getBytes(), null);
    attachConnection(secondMessage, CONNECTION_B_CLIENT, CONNECTION_B_SERVER);

    var start = Instant.now();
    converter.convertElement(secondMessage);
    var elapsed = Duration.between(start, Instant.now());

    assertThat(result)
        .as("no message on connection B precedes it, so there is nothing to find")
        .hasValue(Optional.empty());
    assertThat(elapsed)
        .as(
            "a stuck message on an unrelated connection must not be waited for at all when"
                + " looking for a predecessor on a different connection")
        .isLessThan(Duration.ofMillis(300));
  }

  private void addStuckPredecessor(
      RbelConverter converter, RbelSocketAddress sender, RbelSocketAddress receiver) {
    var predecessor = new RbelElement("stuck".getBytes(), null);
    predecessor.setConversionPhase(RbelConversionPhase.CONTENT_PARSING);
    attachConnection(predecessor, sender, receiver);
    converter.getHistory().addMessageToHistory(predecessor, ZonedDateTime.now());
  }

  private void attachConnection(
      RbelElement element, RbelSocketAddress sender, RbelSocketAddress receiver) {
    element.addFacet(
        RbelTcpIpMessageFacet.builder()
            .sender(RbelSocketAddressFacet.buildRbelSocketAddressFacet(element, sender))
            .receiver(RbelSocketAddressFacet.buildRbelSocketAddressFacet(element, receiver))
            .build());
  }
}
