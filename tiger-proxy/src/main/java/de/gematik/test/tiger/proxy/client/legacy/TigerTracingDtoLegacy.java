/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy.client.legacy;

import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.ProxyTransmissionHistory;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.proxy.client.TigerTracingDto;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Legacy DTO for Tiger Tracing messages. We changed the structure of the tracing messages in tiger
 * version 3.8.0. If we need to subscribe to the traffic of the tiger proxy of an old version we
 * receive the data in this TigerTracingDtoLegacy and convert it to the new format.
 */
@Data
@Builder
public class TigerTracingDtoLegacy {

  private final String requestUuid;
  private final String responseUuid;

  @JsonDeserialize(using = TigerTracingDtoLegacyAddressDeserializer.class)
  private final RbelSocketAddress sender;

  @JsonDeserialize(using = TigerTracingDtoLegacyAddressDeserializer.class)
  private final RbelSocketAddress receiver;

  @JsonDeserialize(using = TigerTracingDtoLegcyDatetimeDeserializer.class)
  private final ZonedDateTime requestTransmissionTime;

  @JsonDeserialize(using = TigerTracingDtoLegcyDatetimeDeserializer.class)
  private final ZonedDateTime responseTransmissionTime;

  private final Map<String, String> additionalInformationRequest;
  private final Map<String, String> additionalInformationResponse;
  private final Long sequenceNumberRequest;
  private final Long sequenceNumberResponse;
  private final ProxyTransmissionHistory proxyTransmissionHistoryRequest;
  private final ProxyTransmissionHistory proxyTransmissionHistoryResponse;
  @Builder.Default private final boolean unparsedChunk = false;

  /** returns list with request and response dto */
  public List<TigerTracingDto> toDtoList() {
    val messages = new ArrayList<TigerTracingDto>();
    messages.add(
        TigerTracingDto.builder()
            .messageUuid(requestUuid)
            .sender(sender)
            .receiver(receiver)
            .additionalInformation(
                mergeMaps(
                    additionalInformationRequest,
                    Map.of(
                        RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(),
                        requestTransmissionTime,
                        RbelMessageMetadata.PAIRED_MESSAGE_UUID.getKey(),
                        responseUuid)))
            .sequenceNumber(sequenceNumberRequest)
            .proxyTransmissionHistory(proxyTransmissionHistoryRequest)
            .request(true)
            .build());
    if (StringUtils.isNotBlank(responseUuid)) {
      messages.add(
          TigerTracingDto.builder()
              .messageUuid(responseUuid)
              .sender(receiver)
              .receiver(sender)
              .additionalInformation(
                  mergeMaps(
                      additionalInformationResponse,
                      Map.of(
                          RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(),
                              responseTransmissionTime,
                          RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.getKey(), requestUuid,
                          RbelMessageMetadata.PAIRED_MESSAGE_UUID.getKey(), requestUuid)))
              .sequenceNumber(sequenceNumberResponse)
              .proxyTransmissionHistory(proxyTransmissionHistoryResponse)
              .request(false)
              .build());
    }
    return messages;
  }

  private Map<String, Object> mergeMaps(Map<String, String> first, Map<String, Object> second) {
    val result = new HashMap<String, Object>();
    result.putAll(first);
    result.putAll(second);
    return result;
  }

  private static class TigerTracingDtoLegacyAddressDeserializer
      extends ValueDeserializer<RbelSocketAddress> {

    @Override
    public RbelSocketAddress deserialize(JsonParser p, DeserializationContext ctxt) {
      JsonNode node = ctxt.readTree(p);
      if (node.has("hostname") && node.has("port")) {
        // Extract fields from the legacy object structure
        String hostname = node.get("hostname").asString();
        int port = node.get("port").asInt();
        return RbelSocketAddress.create(hostname, port);
      }
      return null;
    }
  }

  private static class TigerTracingDtoLegcyDatetimeDeserializer
      extends ValueDeserializer<ZonedDateTime> {

    @Override
    public ZonedDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
      try {
        String date = p.getString();
        return ZonedDateTime.parse(date);
      } catch (JacksonException e) {
        return null;
      }
    }
  }
}
