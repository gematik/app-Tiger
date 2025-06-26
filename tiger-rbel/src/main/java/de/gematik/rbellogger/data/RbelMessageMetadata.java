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
package de.gematik.rbellogger.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.rbellogger.data.core.RbelFacet;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * This facet is used to store metadata about the message, such as sender, receiver, transmission.
 * It must only be used for a message, never for a child element! If you want to access metadata
 * when parsing a child element, search for the parent node instead!
 */
@Slf4j
public class RbelMessageMetadata implements RbelFacet {
  public static final RbelMetadataValue<RbelHostname> MESSAGE_SENDER =
      new RbelMetadataValue<>("senderHostname", RbelHostname.class);
  public static final RbelMetadataValue<RbelHostname> MESSAGE_RECEIVER =
      new RbelMetadataValue<>("receiverHostname", RbelHostname.class);
  public static final RbelMetadataValue<ZonedDateTime> MESSAGE_TRANSMISSION_TIME =
      new RbelMetadataValue<>("timestamp", ZonedDateTime.class);
  public static final RbelMetadataValue<String> PREVIOUS_MESSAGE_UUID =
      new RbelMetadataValue<>("previousMessageUuid", String.class);
  public static final RbelMetadataValue<String> PAIRED_MESSAGE_UUID =
      new RbelMetadataValue<>("pairedMessageUuid", String.class);
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private final Map<String, Object> metadata = new HashMap<>();

  public void addMetadata(String key, Object value) {
    if (value != null) {
      metadata.put(key, value);
    } else {
      metadata.remove(key);
    }
  }

  public Object getMetadata(String key) {
    return metadata.get(key);
  }

  public RbelMessageMetadata withSender(RbelHostname sender) {
    addMetadata(MESSAGE_SENDER.getKey(), sender);
    return this;
  }

  public Optional<RbelHostname> getSender() {
    return MESSAGE_SENDER.getValue(this);
  }

  public RbelMessageMetadata withReceiver(RbelHostname receiver) {
    addMetadata(MESSAGE_RECEIVER.getKey(), receiver);
    return this;
  }

  public Optional<RbelHostname> getReceiver() {
    return MESSAGE_RECEIVER.getValue(this);
  }

  public RbelMessageMetadata withTransmissionTime(ZonedDateTime timestamp) {
    addMetadata(MESSAGE_TRANSMISSION_TIME.getKey(), timestamp);
    return this;
  }

  public Optional<ZonedDateTime> getTransmissionTime() {
    return MESSAGE_TRANSMISSION_TIME.getValue(this);
  }

  public void forEach(BiConsumer<String, Object> consumer) {
    metadata.forEach(consumer);
  }

  public RbelMessageMetadata withPreviousMessage(String uuid) {
    addMetadata(PREVIOUS_MESSAGE_UUID.getKey(), uuid);
    return this;
  }

  public Optional<String> getPreviousMessage() {
    return PREVIOUS_MESSAGE_UUID.getValue(this);
  }

  public RbelMessageMetadata withPairedMessage(String uuid) {
    addMetadata(PAIRED_MESSAGE_UUID.getKey(), uuid);
    return this;
  }

  public Map<String, String> toMap() {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
      if (entry.getValue() != null) {
        result.put(entry.getKey(), entry.getValue().toString());
      }
    }
    return result;
  }

  @Data
  public static class RbelMetadataValue<T> {
    private final String key;
    private final Class<T> type;

    public Optional<T> getValue(RbelMessageMetadata metadata) {
      final Object result = metadata.getMetadata(key);
      if (result == null) {
        return Optional.empty();
      } else if (result.getClass().isAssignableFrom(type)) {
        return Optional.of((T) result); // NOSONAR
      } else {
        try {
          return Optional.ofNullable(MAPPER.convertValue(result, type));
        } catch (Exception e) {
          log.info("Failed to convert metadata value for key {} with content {}", key, result);
          throw new RuntimeException(
              "Metadata Value for key "
                  + key
                  + " is not of type "
                  + type.getName()
                  + ", but rather "
                  + result.getClass().getName(),
              e);
        }
      }
    }

    public void putValue(RbelMessageMetadata metadata, T value) {
      metadata.addMetadata(key, value);
    }
  }
}
