/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@AllArgsConstructor
@Slf4j
public class RbelFileWriter {

    private static final String FILE_DIVIDER = "\n";
    private static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
    private static final String SENDER_HOSTNAME = "senderHostname";
    private static final String RECEIVER_HOSTNAME = "receiverHostname";
    private static final String SEQUENCE_NUMBER = "sequenceNumber";
    private static final String MESSAGE_TIME = "timestamp";
    private static final String MESSAGE_UUID = "uuid";
    public final List<RbelMessagePostProcessor> postConversionListener = new ArrayList<>();
    public final List<BiConsumer<RbelElement, JSONObject>> preSaveListener = new ArrayList<>();
    private final RbelConverter rbelConverter;

    public String convertToRbelFileString(RbelElement rbelElement) {
        final JSONObject jsonObject = new JSONObject(Map.of(
            RAW_MESSAGE_CONTENT, Base64.getEncoder().encodeToString(rbelElement.getRawContent()),
            SENDER_HOSTNAME, rbelElement.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getSender)
                .filter(Objects::nonNull)
                .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
                .map(RbelHostnameFacet::toString)
                .orElse(""),
            RECEIVER_HOSTNAME, rbelElement.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getReceiver)
                .filter(Objects::nonNull)
                .flatMap(element -> element.getFacet(RbelHostnameFacet.class))
                .map(RbelHostnameFacet::toString)
                .orElse(""),
            SEQUENCE_NUMBER, rbelElement.getFacet(RbelTcpIpMessageFacet.class)
                .map(RbelTcpIpMessageFacet::getSequenceNumber)
                .map(Object::toString)
                .orElse(""),
            MESSAGE_TIME, rbelElement.getFacet(RbelMessageTimingFacet.class)
                .map(RbelMessageTimingFacet::getTransmissionTime)
                .map(Object::toString)
                .orElse(""),
            MESSAGE_UUID, rbelElement.getUuid()
        ));
        preSaveListener.forEach(listener -> listener.accept(rbelElement, jsonObject));

        return jsonObject + FILE_DIVIDER;
    }

    public List<RbelElement> convertFromRbelFile(Path rbelFile) {
        try {
            return readRbelFileStream(Files.lines(rbelFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RbelFileReadingException("Error while reading from file " + rbelFile.toAbsolutePath(), e);
        }
    }

    public List<RbelElement> convertFromRbelFile(String rbelFileContent) {
        return readRbelFileStream(Arrays.stream(rbelFileContent.split(FILE_DIVIDER)));
    }

    private List<RbelElement> readRbelFileStream(Stream<String> rbelFileStream) {
        final List<String> rawMessageStrings = rbelFileStream
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toList());
        List<RbelElement> list = new ArrayList<>();
        log.info("Found {} messages in file, starting parsing...", rawMessageStrings.size());
        int numberOfParsedMessages = 0;
        for (String rawMessageString : rawMessageStrings) {
            if ((++numberOfParsedMessages % 1000) == 0) {
                log.info("Parsed {} messages, continuing...", numberOfParsedMessages);
            }
            JSONObject jsonObject = new JSONObject(rawMessageString);
            Optional<RbelElement> rbelElement = parseFileObject(jsonObject);
            if (rbelElement.isPresent()) {
                RbelElement element = rbelElement.get();
                list.add(element);
            }
        }
        return list;
    }

    private Optional<RbelElement> parseFileObject(JSONObject messageObject) {
        try {
            final String msgUuid = messageObject.optString(MESSAGE_UUID);
            if (rbelConverter.isMessageUuidAlreadyKnown(msgUuid)) {
                return Optional.empty();
            }
            final RbelElement parsedMessage = rbelConverter.parseMessage(
                RbelElement.builder()
                    .rawContent(Base64.getDecoder().decode(messageObject.getString(RAW_MESSAGE_CONTENT)))
                    .uuid(msgUuid)
                    .parentNode(null)
                    .build(),
                RbelHostname.fromString(messageObject.getString(SENDER_HOSTNAME)).orElse(null),
                RbelHostname.fromString(messageObject.getString(RECEIVER_HOSTNAME)).orElse(null),
                messageObject.has(MESSAGE_TIME) ?
                    parseTransmissionTimeFromString(messageObject.getString(MESSAGE_TIME)) :
                    Optional.empty());
            postConversionListener
                .forEach(listener -> listener.performMessagePostConversionProcessing(
                    parsedMessage, rbelConverter, messageObject));
            return Optional.of(parsedMessage);
        } catch (Exception e) {
            throw new RbelFileReadingException(
                "Error while converting from object '" + messageObject.toString() + "'", e);
        }
    }

    private static Optional<ZonedDateTime> parseTransmissionTimeFromString(String time) {
        if (StringUtils.isBlank(time)) {
            return Optional.empty();
        }
        return Optional.of(ZonedDateTime.parse(time));
    }

    private static class RbelFileReadingException extends RuntimeException {

        public RbelFileReadingException(String s, Exception e) {
            super(s, e);
        }
    }
}
