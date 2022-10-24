/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelFileWriterUtils {

    public final static String FILE_DIVIDER = "\n";
    private static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
    private static final String SENDER_HOSTNAME = "senderHostname";
    private static final String RECEIVER_HOSTNAME = "receiverHostname";
    private static final String SEQUENCE_NUMBER = "sequenceNumber";
    private static final String MESSAGE_TIME = "timestamp";
    private static final String MESSAGE_UUID = "uuid";

    public static String convertToRbelFileString(RbelElement rbelElement) {
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
        return jsonObject + FILE_DIVIDER;
    }

    public static List<RbelElement> convertFromRbelFile(Path rbelFile, RbelConverter rbelConverter) {
        try {
            return readRbelFileStream(Files.lines(rbelFile, StandardCharsets.UTF_8), rbelConverter);
        } catch (IOException e) {
            throw new RbelFileReadingException("Error while reading from file " + rbelFile.toAbsolutePath(), e);
        }
    }

    public static List<RbelElement> convertFromRbelFile(String rbelFileContent, RbelConverter rbelConverter) {
        return readRbelFileStream(Arrays.stream(rbelFileContent.split(FILE_DIVIDER)), rbelConverter);
    }

    private static List<RbelElement> readRbelFileStream(Stream<String> rbelFileStream, RbelConverter rbelConverter) {
        final List<String> collect = rbelFileStream
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.toList());
        return collect.stream()
            .map(JSONObject::new)
            .map(content -> parseFileObject(rbelConverter, content))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private static Optional<RbelElement> parseFileObject(RbelConverter rbelConverter, JSONObject messageObject) {
        try {
            final String msgUuid = messageObject.optString(MESSAGE_UUID);
            if (new ArrayList<>(rbelConverter.getMessageHistory()).stream()
                .anyMatch(msg -> msg.getUuid().equals(msgUuid))) {
                return Optional.empty();
            }
            return Optional.of(rbelConverter.parseMessage(
                    RbelElement.builder()
                        .rawContent(Base64.getDecoder().decode(messageObject.getString(RAW_MESSAGE_CONTENT)))
                        .uuid(msgUuid)
                        .parentNode(null)
                        .build(),
                    RbelHostname.fromString(messageObject.getString(SENDER_HOSTNAME)).orElse(null),
                    RbelHostname.fromString(messageObject.getString(RECEIVER_HOSTNAME)).orElse(null),
                    messageObject.has(MESSAGE_TIME) ?
                        parseTransmissionTimeFromString(messageObject.getString(MESSAGE_TIME)) :
                        Optional.empty()
                )
            );
        } catch (Exception e) {
            throw new RbelFileReadingException("Error while converting from object '" + messageObject.toString() + "'",
                e);
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
