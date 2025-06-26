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
package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.data.RbelMessageMetadata.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.data.facet.RbelNonTransmissionMarkerFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@AllArgsConstructor
@Slf4j
public class RbelFileWriter {
  private static final String FILE_DIVIDER = "\n";
  public static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
  public static final String SEQUENCE_NUMBER = "sequenceNumber";
  public static final String MESSAGE_TIME = "timestamp";
  public static final String MESSAGE_UUID = "uuid";

  private final RbelConverter rbelConverter;

  public String convertToRbelFileString(RbelElement rbelElement) {
    final JSONObject jsonObject =
        new JSONObject(
            Map.of(
                RAW_MESSAGE_CONTENT,
                    Base64.getEncoder().encodeToString(rbelElement.getRawContent()),
                MESSAGE_UUID, rbelElement.getUuid()));
    rbelElement
        .getFacet(RbelMessageMetadata.class)
        .ifPresent(metadata -> metadata.forEach(jsonObject::put));

    return jsonObject + FILE_DIVIDER;
  }

  public List<RbelElement> convertFromRbelFile(
      String rbelFileContent, Optional<String> readFilter) {
    // not parallel stream: we want to keep the order of the messages!
    return readRbelFileStream(Arrays.stream(rbelFileContent.split(FILE_DIVIDER)), readFilter);
  }

  private List<RbelElement> readRbelFileStream(
      Stream<String> rbelFileStream, Optional<String> readFilter) {
    final List<String> rawMessageStrings = rbelFileStream.filter(StringUtils::isNotBlank).toList();
    log.info("Found {} messages in file, starting parsing...", rawMessageStrings.size());
    AtomicInteger numberOfParsedMessages = new AtomicInteger(0);
    final List<RbelElement> list =
        rawMessageStrings.stream()
            .peek( // NOSONAR
                str -> {
                  if (numberOfParsedMessages.get() > 0
                      && (numberOfParsedMessages.getAndIncrement() % 500) == 0) {
                    log.info("Parsed {} messages, continuing...", numberOfParsedMessages);
                  }
                })
            .map(JSONObject::new)
            .sorted(Comparator.comparing(json -> json.optInt(SEQUENCE_NUMBER, Integer.MAX_VALUE)))
            .map(messageObject -> parseFileObject(messageObject, readFilter))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    log.info(
        "Parsing complete, parsed {} messages of {} available",
        list.size(),
        rawMessageStrings.size());
    return list;
  }

  private Optional<RbelElement> parseFileObject(
      JSONObject messageObject, Optional<String> readFilter) {
    try {
      final String msgUuid = messageObject.optString(MESSAGE_UUID);

      if (rbelConverter.isMessageUuidAlreadyKnown(msgUuid)) {
        return Optional.empty();
      }

      final RbelElement rawMessageObject =
          RbelElement.builder()
              .rawContent(Base64.getDecoder().decode(messageObject.getString(RAW_MESSAGE_CONTENT)))
              .uuid(msgUuid)
              .parentNode(null)
              .build();
      rawMessageObject.addFacet(new IncompleteMessageReadFromFile());

      readFilter.ifPresent(
          filterCriterion ->
              rawMessageObject.addFacet(
                  new ProxyFileReadingFilter.TgrFileFilterFacet(filterCriterion)));

      val messageMetadata = new RbelMessageMetadata();
      enrichMetadataFromJson(messageMetadata, messageObject);

      final RbelElement parsedMessage =
          rbelConverter.parseMessage(rawMessageObject, messageMetadata);

      parsedMessage.removeFacetsOfType(IncompleteMessageReadFromFile.class);
      return Optional.of(parsedMessage);
    } catch (Exception e) {
      throw new RbelFileReadingException(
          "Error while converting from object '" + messageObject.toString() + "'", e);
    }
  }

  private void enrichMetadataFromJson(
      RbelMessageMetadata messageMetadata, JSONObject messageObject) {
    for (val key : messageObject.keySet()) {
      messageMetadata.addMetadata(key, messageObject.get(key));
    }
  }

  public static class TgrFilePairingPlugin extends RbelConverterPlugin {
    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.PREPARATION;
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
      if (metadataFacet.isEmpty() || !rbelElement.hasFacet(IncompleteMessageReadFromFile.class)) {
        return;
      }
      final Optional<String> value = PAIRED_MESSAGE_UUID.getValue(metadataFacet.get());
      if (value.isPresent()) {
        converter
            .findMessageByUuid(value.get())
            .ifPresent(
                other -> {
                  TracingMessagePairFacet pair;
                  if (other.hasFacet(RbelRequestFacet.class)) {
                    pair = new TracingMessagePairFacet(rbelElement, other);
                  } else {
                    pair = new TracingMessagePairFacet(other, rbelElement);
                  }
                  rbelElement.addOrReplaceFacet(pair);
                  other.addOrReplaceFacet(pair);
                });
      } else {
        pairPotentialHttpResponseWithPreviousMessage(rbelElement, converter);
      }
    }

    public void pairPotentialHttpResponseWithPreviousMessage(
        RbelElement message, RbelConversionExecutor converter) {
      if (Optional.ofNullable(message.getRawStringContent())
          .map(content -> content.startsWith("HTTP/"))
          .orElse(false)) {
        converter
            .messagesStreamLatestFirst()
            .dropWhile(candidate -> candidate != message)
            .filter(msg -> msg.hasFacet(RbelHttpRequestFacet.class))
            .filter(msg -> !msg.hasFacet(TracingMessagePairFacet.class))
            .findFirst()
            .ifPresent(
                previousMessage -> {
                  message.addOrReplaceFacet(new TracingMessagePairFacet(message, previousMessage));
                  previousMessage.addOrReplaceFacet(
                      new TracingMessagePairFacet(message, previousMessage));
                });
      }
    }
  }

  public static class TgrFilePairingWriterPlugin extends RbelConverterPlugin {
    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.TRANSMISSION;
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
      if (metadataFacet.isEmpty() || rbelElement.hasFacet(RbelNonTransmissionMarkerFacet.class)) {
        return;
      }
      rbelElement
          .getFacet(TracingMessagePairFacet.class)
          .flatMap(facet -> facet.getOtherMessage(rbelElement))
          .map(RbelElement::getUuid)
          .ifPresent(uuid -> PAIRED_MESSAGE_UUID.putValue(metadataFacet.get(), uuid));
    }
  }

  private static class RbelFileReadingException extends GenericTigerException {
    public RbelFileReadingException(String s, Exception e) {
      super(s, e);
    }
  }

  /**
   * Simple marker facet to indicate that the message was read from a file. Should always be deleted
   * after parsing is complete.
   */
  public static class IncompleteMessageReadFromFile implements RbelFacet {}
}
