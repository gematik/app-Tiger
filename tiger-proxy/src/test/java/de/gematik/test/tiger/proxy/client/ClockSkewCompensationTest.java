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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockSkewCompensationTest {

  @Test
  @DisplayName("TracingMessagePreparationHandler adjusts metadata timestamp via facet")
  void preparationHandler_adjustsMetadataTimestamp() {
    Duration offset = Duration.ofSeconds(7);
    ZonedDateTime remoteTimestamp = ZonedDateTime.now().plusSeconds(7); // simulated remote time

    TigerRemoteProxyClient mockClient = mock(TigerRemoteProxyClient.class);
    when(mockClient.getRemoteClockOffset()).thenReturn(offset);

    RbelMessageMetadata metadata = new RbelMessageMetadata();
    metadata.withTransmissionTime(remoteTimestamp);

    var handler = new TracingMessageFrame.TracingMessagePreparationHandler();
    RbelElement msg = RbelElement.builder().uuid("test-uuid").rawContent(new byte[0]).build();
    msg.addFacet(metadata);

    PartialTracingMessage partialMsg = mock(PartialTracingMessage.class);
    when(partialMsg.getAdditionalInformation())
        .thenReturn(
            java.util.Map.of(
                RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(), remoteTimestamp));

    msg.addFacet(new TracingMessageFrame.MeshMessagePostProcessingFacet(partialMsg, mockClient));

    handler.consumeElement(msg, null);

    ZonedDateTime adjustedTime =
        RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME
            .getValue(msg.getFacetOrFail(RbelMessageMetadata.class))
            .orElseThrow();

    assertThat(adjustedTime)
        .as("Timestamp should be adjusted by subtracting the 7s offset")
        .isCloseTo(
            remoteTimestamp.minus(offset),
            org.assertj.core.api.Assertions.within(100, ChronoUnit.MILLIS));
  }
}
