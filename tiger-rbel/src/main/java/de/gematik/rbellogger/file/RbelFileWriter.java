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
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.util.TigerVersionProvider;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class RbelFileWriter {
  private static final String FILE_DIVIDER = "\n";
  public static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
  public static final String SEQUENCE_NUMBER = "sequenceNumber";
  public static final String MESSAGE_TIME = "timestamp";
  public static final String MESSAGE_UUID = "uuid";
  public static final String TIGER_VERSION_KEY = "tigerVersion";
  private static final byte[] HTTP_PREFIX = "HTTP/".getBytes();

  private final RbelConverter rbelConverter;
  private final AtomicBoolean versionHeaderWritten = new AtomicBoolean();
  private final AtomicReference<String> lastReadTigerVersion = new AtomicReference<>();
  private boolean writeVersionHeader = true;

  public RbelFileWriter(RbelConverter rbelConverter) {
    this.rbelConverter = rbelConverter;
  }

  /**
   * Sets whether the version header should be written. Default is true. Set to false to skip
   * version header generation (e.g., for downloads).
   *
   * @param writeVersionHeader true to write version header, false to skip
   * @return this RbelFileWriter for method chaining
   */
  public RbelFileWriter setWriteVersionHeader(boolean writeVersionHeader) {
    this.writeVersionHeader = writeVersionHeader;
    return this;
  }

  /**
   * Returns the Tiger version string that was read from the last log file. Returns empty if no
   * version was found.
   */
  public Optional<String> getLastReadTigerVersion() {
    return Optional.ofNullable(lastReadTigerVersion.get());
  }

  public String convertToRbelFileString(RbelElement rbelElement) {
    return convertToRbelFileString(rbelElement, Long.MAX_VALUE);
  }

  @SneakyThrows
  public String convertToRbelFileString(RbelElement rbelElement, long skipContentThreshold) {
    final JSONObject jsonObject = new JSONObject(Map.of(MESSAGE_UUID, rbelElement.getUuid()));
    if (rbelElement.getSize() <= skipContentThreshold) {
      jsonObject.put(RAW_MESSAGE_CONTENT, encodeToBase64(rbelElement.getContent()));
    }
    rbelElement
        .getFacet(RbelMessageMetadata.class)
        .ifPresent(metadata -> metadata.forEach(jsonObject::put));

    if (writeVersionHeader && versionHeaderWritten.compareAndSet(false, true)) {
      if (!jsonObject.has(SEQUENCE_NUMBER)) {
        jsonObject.put(SEQUENCE_NUMBER, -1);
      }
      jsonObject.put(TIGER_VERSION_KEY, TigerVersionProvider.getTigerVersionString());
    }

    return jsonObject + FILE_DIVIDER;
  }

  @SneakyThrows
  private String encodeToBase64(RbelContent content) {
    try (var out = new ByteArrayOutputStream();
        var in = content.toInputStream()) {
      try (var encoding = Base64.getEncoder().wrap(out)) {
        in.transferTo(encoding);
      }
      return out.toString();
    }
  }

  public List<RbelElement> convertFromRbelFile(
      String rbelFileContent, Optional<String> readFilter) {
    // not parallel stream: we want to keep the order of the messages!
    return convertRbelFileEntries(rbelFileContent.lines(), readFilter, null);
  }

  public List<RbelElement> convertFromRbelFile(
      Reader rbelFileContent,
      Optional<String> readFilter,
      Function<String, RbelContent> contentProvider) {
    return convertRbelFileEntries(
        new BufferedReader(rbelFileContent).lines(), readFilter, contentProvider);
  }

  public List<RbelElement> convertRbelFileEntries(
      Stream<String> rbelFileLines,
      Optional<String> readFilter,
      Function<String, RbelContent> contentProvider) {
    log.info("Starting parsing...");
    AtomicInteger numberOfParsedMessages = new AtomicInteger(0);
    final List<RbelElement> list =
        getRbelElementStream(
                rbelFileLines,
                readFilter,
                contentProvider,
                element -> {
                  if (numberOfParsedMessages.get() > 0
                      && (numberOfParsedMessages.getAndIncrement() % 500) == 0) {
                    log.info("Parsed {} messages, continuing...", numberOfParsedMessages);
                  }
                })
            .toList();
    log.info("Parsing complete, parsed {} messages", list.size());
    return list;
  }

  public @NotNull Stream<RbelElement> getRbelElementStream(
      Stream<String> rbelFileLines,
      Optional<String> readFilter,
      Function<String, RbelContent> contentProvider,
      Consumer<? super Optional<RbelElement>> onEveryMessageParsed) {
    return rbelFileLines
        .filter(StringUtils::isNotBlank)
        .map(JSONObject::new)
        .filter(this::isMessageObject)
        .sorted(Comparator.comparing(json -> json.optInt(SEQUENCE_NUMBER, Integer.MAX_VALUE)))
        .map(messageObject -> parseFileObject(messageObject, readFilter, contentProvider))
        .filter(Optional::isPresent)
        .peek(onEveryMessageParsed)
        .map(Optional::get);
  }

  private boolean isMessageObject(JSONObject messageObject) {
    return messageObject.has(RAW_MESSAGE_CONTENT)
        || messageObject.has(SEQUENCE_NUMBER)
        || messageObject.has(MESSAGE_UUID);
  }

  private Optional<RbelElement> parseFileObject(
      JSONObject messageObject,
      Optional<String> readFilter,
      Function<String, RbelContent> contentProvider) {
    try {
      if (messageObject.has(TIGER_VERSION_KEY)) {
        var version = messageObject.getString(TIGER_VERSION_KEY);
        lastReadTigerVersion.set(version);
        log.info("Detected Tiger version '{}' in log file.", version);
      }

      final String msgUuid = messageObject.optString(MESSAGE_UUID);

      if (rbelConverter.getKnownMessageUuids().add(msgUuid)) {
        return getContent(messageObject, msgUuid, contentProvider)
            .map(content -> parseContent(content, messageObject, readFilter, msgUuid));
      } else {
        log.atDebug().log("Skipping conversion for already known message uuid: {}", msgUuid);
        return Optional.empty();
      }

    } catch (Exception e) {
      throw new RbelFileReadingException(
          "Error while converting from object '" + messageObject.toString() + "'", e);
    }
  }

  private Optional<RbelContent> getContent(
      JSONObject messageObject, String msgUuid, Function<String, RbelContent> contentProvider)
      throws IOException {
    if (!messageObject.has(RAW_MESSAGE_CONTENT)) {
      if (contentProvider == null) {
        log.warn("No message content provider found for message uuid: {}", msgUuid);
        return Optional.empty();
      } else {
        try {
          var content = contentProvider.apply(msgUuid);
          if (content == null) {
            log.warn("No content found for message uuid: {}", msgUuid);
            return Optional.empty();
          }
          return Optional.of(content);
        } catch (Exception e) {
          log.error("Error while reading content for message uuid: {}", msgUuid, e);
          return Optional.empty();
        }
      }
    } else {
      return Optional.of(
          RbelContent.from(
              Base64.getDecoder()
                  .wrap(
                      ReaderInputStream.builder()
                          .setReader(new StringReader(messageObject.getString(RAW_MESSAGE_CONTENT)))
                          .get())));
    }
  }

  private RbelElement parseContent(
      RbelContent content, JSONObject messageObject, Optional<String> readFilter, String msgUuid) {
    final RbelElement rawMessageObject =
        RbelElement.builder().content(content).uuid(msgUuid).parentNode(null).build();
    rawMessageObject.addFacet(new IncompleteMessageReadFromFile());

    readFilter.ifPresent(
        filterCriterion ->
            rawMessageObject.addFacet(
                new ProxyFileReadingFilter.TgrFileFilterFacet(filterCriterion)));

    val messageMetadata = new RbelMessageMetadata();
    enrichMetadataFromJson(messageMetadata, messageObject);

    final RbelElement parsedMessage = rbelConverter.parseMessage(rawMessageObject, messageMetadata);

    parsedMessage.removeFacetsOfType(IncompleteMessageReadFromFile.class);
    return parsedMessage;
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
      if (message.getContent().startsWith(HTTP_PREFIX)) {
        converter
            .getConverter()
            .getPreviousMessages(
                message,
                msg ->
                    msg.hasFacet(RbelHttpRequestFacet.class)
                        && !msg.hasFacet(TracingMessagePairFacet.class))
            .findFirst()
            .ifPresent(
                unpairedRequest -> {
                  message.addOrReplaceFacet(new TracingMessagePairFacet(message, unpairedRequest));
                  unpairedRequest.addOrReplaceFacet(
                      new TracingMessagePairFacet(message, unpairedRequest));
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
