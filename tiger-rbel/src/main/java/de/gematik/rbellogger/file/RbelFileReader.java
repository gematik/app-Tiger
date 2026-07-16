/*
 *
 * Copyright 2026 gematik GmbH
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

import static de.gematik.rbellogger.file.RbelFileWriter.*;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.util.RbelContent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

/** Reads and parses .tgr files back into {@link RbelElement} messages. */
@Slf4j
public class RbelFileReader {

  private final RbelConverter rbelConverter;
  private final AtomicReference<String> lastReadTigerVersion = new AtomicReference<>();

  public RbelFileReader(RbelConverter rbelConverter) {
    this.rbelConverter = rbelConverter;
  }

  /**
   * Returns the Tiger version string that was read from the last log file. Returns empty if no
   * version was found.
   */
  public Optional<String> getLastReadTigerVersion() {
    return Optional.ofNullable(lastReadTigerVersion.get());
  }

  public List<RbelElement> convertFromRbelFile(
      String rbelFileContent, Optional<String> readFilter) {
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
        .filter(RbelFileReader::isMessageObject)
        .sorted(Comparator.comparing(json -> json.optInt(SEQUENCE_NUMBER, Integer.MAX_VALUE)))
        .map(messageObject -> parseFileObject(messageObject, readFilter, contentProvider))
        .filter(Optional::isPresent)
        .peek(onEveryMessageParsed)
        .map(Optional::get)
        .filter(rbelElement -> rbelElement.getConversionPhase() != RbelConversionPhase.DELETED);
  }

  private static boolean isMessageObject(JSONObject messageObject) {
    return messageObject.has(RAW_MESSAGE_CONTENT)
        || messageObject.has(SEQUENCE_NUMBER)
        || messageObject.has(MESSAGE_UUID);
  }

  private Optional<RbelElement> parseFileObject(
      JSONObject messageObject,
      Optional<String> readFilter,
      Function<String, RbelContent> contentProvider) {
    try {
      extractVersionIfPresent(messageObject);

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
          "Error while converting from object '" + messageObject + "'", e);
    }
  }

  private void extractVersionIfPresent(JSONObject messageObject) {
    if (messageObject.has(TIGER_VERSION_KEY)) {
      var version = messageObject.getString(TIGER_VERSION_KEY);
      lastReadTigerVersion.set(version);
      log.info("Detected Tiger version '{}' in log file.", version);
    }
  }

  private Optional<RbelContent> getContent(
      JSONObject messageObject, String msgUuid, Function<String, RbelContent> contentProvider)
      throws IOException {
    if (!messageObject.has(RAW_MESSAGE_CONTENT)) {
      return getContentFromProvider(msgUuid, contentProvider);
    }
    return Optional.of(decodeBase64Content(messageObject.getString(RAW_MESSAGE_CONTENT)));
  }

  private Optional<RbelContent> getContentFromProvider(
      String msgUuid, Function<String, RbelContent> contentProvider) {
    if (contentProvider == null) {
      log.warn("No message content provider found for message uuid: {}", msgUuid);
      return Optional.empty();
    }
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

  private RbelContent decodeBase64Content(String base64) throws IOException {
    return RbelContent.from(
        Base64.getDecoder()
            .wrap(ReaderInputStream.builder().setReader(new StringReader(base64)).get()));
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
}
