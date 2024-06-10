/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.file;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@AllArgsConstructor
@Slf4j
public class RbelFileWriter {
  private static final String FILE_DIVIDER = "\n";
  public static final String RAW_MESSAGE_CONTENT = "rawMessageContent";
  public static final String SENDER_HOSTNAME = "senderHostname";
  public static final String RECEIVER_HOSTNAME = "receiverHostname";
  public static final String SEQUENCE_NUMBER = "sequenceNumber";
  public static final String MESSAGE_TIME = "timestamp";
  public static final String MESSAGE_UUID = "uuid";
  public static final String PAIRED_MESSAGE_UUID = "pairedMessageUuid";
  public static final List<RbelFilePreSaveListener> DEFAULT_PRE_SAVE_LISTENER =
      new ArrayList<>(
          List.of(
              new MessageTimeWriter(),
              new TcpIpMessageFacetWriter(),
              new BundledServerNameWriterAndReader()));
  public static final List<RbelMessagePostProcessor> DEFAULT_POST_CONVERSION_LISTENER =
      new ArrayList<>(List.of(new BundledServerNameWriterAndReader()));
  public final List<RbelMessagePostProcessor> postConversionListener =
      new ArrayList<>(DEFAULT_POST_CONVERSION_LISTENER);
  public final List<RbelFilePreSaveListener> preSaveListener =
      new ArrayList<>(DEFAULT_PRE_SAVE_LISTENER);

  private final RbelConverter rbelConverter;

  public String convertToRbelFileString(RbelElement rbelElement) {
    final JSONObject jsonObject =
        new JSONObject(
            Map.of(
                RAW_MESSAGE_CONTENT,
                    Base64.getEncoder().encodeToString(rbelElement.getRawContent()),
                MESSAGE_UUID, rbelElement.getUuid()));
    preSaveListener.forEach(listener -> listener.preSaveCallback(rbelElement, jsonObject));

    return jsonObject + FILE_DIVIDER;
  }

  public List<RbelElement> convertFromRbelFile(String rbelFileContent) {
    // not parallel stream: we want to keep the order of the messages!
    return readRbelFileStream(Arrays.stream(rbelFileContent.split(FILE_DIVIDER)));
  }

  private List<RbelElement> readRbelFileStream(Stream<String> rbelFileStream) {
    final List<String> rawMessageStrings = rbelFileStream.filter(StringUtils::isNotBlank).toList();
    log.info("Found {} messages in file, starting parsing...", rawMessageStrings.size());
    AtomicInteger numberOfParsedMessages = new AtomicInteger(0);
    final List<RbelElement> list =
        rawMessageStrings.stream()
            .peek( // NOSONAR
                str -> {
                  if ((numberOfParsedMessages.getAndIncrement() % 500) == 0
                      && numberOfParsedMessages.get() > 0) {
                    log.info("Parsed {} messages, continuing...", numberOfParsedMessages);
                  }
                })
            .map(JSONObject::new)
            .sorted(Comparator.comparing(json -> json.optInt(SEQUENCE_NUMBER, Integer.MAX_VALUE)))
            .map(this::parseFileObject)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    log.info(
        "Parsing complete, parsed {} messages of {} available",
        list.size(),
        rawMessageStrings.size());
    return list;
  }

  private Optional<RbelElement> parseFileObject(JSONObject messageObject) {
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

      RbelElementConvertionPair messageToConvert =
          Optional.ofNullable(messageObject.optString(PAIRED_MESSAGE_UUID, null))
              .map(
                  pairedUuid ->
                      new RbelElementConvertionPair(
                          rawMessageObject,
                          CompletableFuture.completedFuture(
                              rbelConverter.findMessageByUuid(pairedUuid).orElse(null))))
              .orElse(
                  new RbelElementByOrderConvertionPair(
                      rawMessageObject, rbelConverter.getMessageList()));

      final RbelElement parsedMessage =
          rbelConverter.parseMessage(
              messageToConvert,
              RbelHostname.fromString(messageObject.getString(SENDER_HOSTNAME)).orElse(null),
              RbelHostname.fromString(messageObject.getString(RECEIVER_HOSTNAME)).orElse(null),
              messageObject.has(MESSAGE_TIME)
                  ? parseTransmissionTimeFromString(messageObject.getString(MESSAGE_TIME))
                  : Optional.empty());
      postConversionListener.forEach(
          listener ->
              listener.performMessagePostConversionProcessing(
                  parsedMessage, rbelConverter, messageObject));
      return Optional.ofNullable(parsedMessage);
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
