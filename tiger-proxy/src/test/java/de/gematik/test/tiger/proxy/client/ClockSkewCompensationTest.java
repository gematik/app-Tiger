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

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import de.gematik.test.tiger.proxy.handler.SingleConnectionParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class ClockSkewCompensationTest {

  private static final Duration OFFSET = Duration.ofSeconds(7);

  private static final byte[] HTTP_REQUEST =
      "GET / HTTP/1.1\r\nHost: server\r\nContent-Length: 0\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8);

  private static final TcpIpConnectionIdentifier CONNECTION =
      new TcpIpConnectionIdentifier(
          RbelSocketAddress.fromString("client:80").orElseThrow(),
          RbelSocketAddress.fromString("server:80").orElseThrow());

  private static final String TRANSMISSION_TIME =
      RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey();

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

  @Test
  @DisplayName("An offset the round-trip cannot resolve is not an offset")
  void offsetSmallerThanTheRoundTripIsNoise() {
    assertThat(
            ClockSkewEstimator.isWithinMeasurementError(Duration.ofMillis(1), Duration.ofMillis(4)))
        .as("two proxies on one machine measure a fraction of a millisecond of pure jitter")
        .isTrue();
    assertThat(
            ClockSkewEstimator.isWithinMeasurementError(
                Duration.ofMillis(-1), Duration.ofMillis(4)))
        .as("the remote clock may just as well measure behind as ahead")
        .isTrue();
  }

  @Test
  @DisplayName("A real offset survives the measurement-error check")
  void offsetLargerThanTheRoundTripIsReal() {
    assertThat(
            ClockSkewEstimator.isWithinMeasurementError(
                Duration.ofSeconds(7), Duration.ofMillis(4)))
        .as("machines drifting seconds apart is what the compensation exists for")
        .isFalse();
    assertThat(
            ClockSkewEstimator.isWithinMeasurementError(Duration.ofMillis(2), Duration.ofMillis(4)))
        .as("exactly half the round-trip is the smallest offset still worth believing")
        .isFalse();
  }

  @Test
  @DisplayName("The compensation subtracts the measured offset")
  void compensationSubtractsTheOffset() {
    var remote = ZonedDateTime.now().plusSeconds(7);

    var compensated =
        ClockSkewEstimator.withCompensatedTransmissionTime(
            Map.of(TRANSMISSION_TIME, remote), OFFSET);

    assertThat(transmissionTimeOf(compensated))
        .isCloseTo(remote.minus(OFFSET), within(100, MILLIS));
  }

  @Test
  @DisplayName("Without a measured offset the traffic is handed over untouched")
  void withoutOffsetTheTrafficIsUntouched() {
    var given = Map.<String, Object>of(TRANSMISSION_TIME, ZonedDateTime.now());

    assertThat(ClockSkewEstimator.withCompensatedTransmissionTime(given, Duration.ZERO))
        .isEqualTo(given);
  }

  @Test
  @DisplayName("The compensation is applied on the way into the parser")
  void compensationIsWiredIntoTheParsingPath() {
    var remote = ZonedDateTime.now().plusSeconds(7);
    var client = clientReadyToParse();

    client.tryParseMessages(tracingMessageTransmittedAt(remote), element -> {});

    assertThat(transmissionTimeOf(trafficHandedToTheBuffer(client)))
        .as(
            "the timing facet every consumer reads is derived from this map during the conversion,"
                + " so compensating afterwards leaves it on the remote clock")
        .isCloseTo(remote.minus(OFFSET), within(100, MILLIS));
  }

  @Test
  @DisplayName("A parsed message carries the compensated time in the facet consumers read")
  void compensatedTimeReachesTheTimingFacet() {
    var remote = ZonedDateTime.now().plusSeconds(7);
    var client = clientReadyToParse();

    client.tryParseMessages(tracingMessageTransmittedAt(remote), element -> {});
    var parsed = parseThroughConnectionParser(trafficHandedToTheBuffer(client));

    assertThat(parsed.getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime())
        .as(
            "the metadata alone is not enough - the log display and the timestamp sort order both"
                + " read the facet, and it is frozen while the message is being parsed")
        .isCloseTo(remote.minus(OFFSET), within(100, MILLIS));
  }

  @SneakyThrows
  private static RbelElement parseThroughConnectionParser(Map<String, Object> additionalData) {
    var executor = Executors.newSingleThreadExecutor();
    try {
      var handler = mock(BinaryExchangeHandler.class);
      when(handler.getTigerProxy()).thenReturn(mock(TigerProxy.class));
      var parser =
          new SingleConnectionParser(
              CONNECTION, executor, RbelLogger.build().getRbelConverter(), handler);
      var entry =
          TcpConnectionEntry.builder()
              .uuid(UUID.randomUUID().toString())
              .data(RbelContent.of(HTTP_REQUEST))
              .connectionIdentifier(CONNECTION)
              .messageKind(RbelMessageKind.REQUEST)
              .build()
              .addAdditionalData(additionalData);
      return parser.bufferNewPart(entry).get().get(0);
    } finally {
      executor.shutdownNow();
    }
  }

  private static ZonedDateTime transmissionTimeOf(Map<String, Object> additionalInformation) {
    return (ZonedDateTime) additionalInformation.get(TRANSMISSION_TIME);
  }

  private static PartialTracingMessage tracingMessageTransmittedAt(ZonedDateTime transmissionTime) {
    var message = mock(PartialTracingMessage.class, RETURNS_DEEP_STUBS);
    when(message.getAdditionalInformation())
        .thenReturn(new HashMap<>(Map.of(TRANSMISSION_TIME, transmissionTime)));
    return message;
  }

  private static TigerRemoteProxyClient clientReadyToParse() {
    var client = mock(TigerRemoteProxyClient.class, RETURNS_DEEP_STUBS);
    giveTheMockALogger(client);
    when(client.getRemoteClockOffset()).thenReturn(OFFSET);
    when(client.getRbelLogger().getRbelConverter().getKnownMessageUuids().isAlreadyConverted(any()))
        .thenReturn(false);
    doCallRealMethod().when(client).tryParseMessages(any(), any());
    return client;
  }

  @SneakyThrows
  private static void giveTheMockALogger(TigerRemoteProxyClient client) {
    var logField = AbstractTigerProxy.class.getDeclaredField("log");
    logField.setAccessible(true);
    logField.set(client, LoggerFactory.getLogger(ClockSkewCompensationTest.class));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> trafficHandedToTheBuffer(TigerRemoteProxyClient client) {
    var captured = ArgumentCaptor.forClass(Map.class);
    verify(client.getBinaryChunksBuffer())
        .addToBuffer(
            any(), any(), any(), any(), captured.capture(), any(), any(), nullable(String.class));
    return captured.getValue();
  }
}
