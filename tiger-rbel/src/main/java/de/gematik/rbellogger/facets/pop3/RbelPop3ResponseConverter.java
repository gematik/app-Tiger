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
package de.gematik.rbellogger.facets.pop3;

import static de.gematik.rbellogger.util.RbelStringUtils.bytesToStringWithoutNonPrintableCharacters;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.util.EmailConversionUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@ConverterInfo(onlyActivateFor = "pop3")
@Slf4j
public class RbelPop3ResponseConverter extends RbelConverterPlugin {

  private static final Pattern STAT_OR_LIST_HEADER =
      Pattern.compile("(?<count>\\d+) ((?<size>\\d+)|messages(:| \\((?<size2>\\d+) octets\\)))");

  private static final Set<RbelPop3Command> MIME_BODY_RESPONSE_COMMANDS =
      Set.of(RbelPop3Command.RETR, RbelPop3Command.TOP);
  private static final byte[] CRLF_DOT_CRLF_BYTES = EmailConversionUtils.CRLF_DOT_CRLF.getBytes();
  public static final byte[] OK_SPACE_PREFIX = "+OK ".getBytes();
  public static final byte[] ERR_PREFIX = "-ERR ".getBytes();
  public static final byte[] OK_PREFIX = "+OK\r\n".getBytes();
  public static final byte[] SPACE_PREFIX = "+ ".getBytes();

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  public static Optional<RbelElement> findAndPairMatchingRequest(
      RbelElement response,
      RbelConversionExecutor context,
      Class<? extends RbelFacet> requestFacetClass) {

    if (response.hasFacet(TracingMessagePairFacet.class)) {
      return Optional.of(response.getFacetOrFail(TracingMessagePairFacet.class).getRequest());
    }
    List<RbelElement> lastMessages =
        context
            .getPreviousMessagesInSameConnectionAs(response)
            .filter(msg -> msg.hasFacet(requestFacetClass))
            .takeWhile(msg -> !msg.hasFacet(TracingMessagePairFacet.class))
            .toList();
    if (lastMessages.isEmpty()) {
      return Optional.empty();
    }
    var request = lastMessages.get(lastMessages.size() - 1);

    var pair = TracingMessagePairFacet.builder().request(request).response(response).build();
    response.addFacet(pair);
    request.addFacet(pair);
    return Optional.of(request);
  }

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    buildPop3ResponseFacet(element, context)
        .ifPresentOrElse(
            pair -> {
              var facet = pair.getLeft();
              var length = pair.getRight();
              element.addFacet(facet);
              element.setUsedBytes(length);
              element.addFacet(new RbelResponseFacet(facet.getStatus().getRawStringContent()));
            },
            () ->
                element
                    .getFacet(TracingMessagePairFacet.class)
                    .ifPresent(
                        pair -> {
                          pair.getRequest().removeFacetsOfType(TracingMessagePairFacet.class);
                          pair.getResponse().removeFacetsOfType(TracingMessagePairFacet.class);
                        }));
  }

  public static class RbelPop3BodyConverter extends RbelConverterPlugin {
    @Override
    public void consumeElement(RbelElement element, RbelConversionExecutor context) {
      element
          .getFacet(RbelPop3ResponseFacet.class)
          .map(RbelPop3ResponseFacet::getBody)
          .filter(
              body ->
                  findPop3Command(element, context)
                      .filter(MIME_BODY_RESPONSE_COMMANDS::contains)
                      .isPresent())
          .ifPresent(context::convertElement);
    }
  }

  private Optional<Pair<RbelPop3ResponseFacet, Integer>> buildPop3ResponseFacet(
      RbelElement element, final RbelConversionExecutor context) {
    return Optional.of(element.getContent())
        .filter(c -> c.size() >= 4)
        .filter(this::startsWithOkOrErrOrSpace)
        .flatMap(c -> getCompleteResponse(element, context))
        .map(s -> s.split(EmailConversionUtils.CRLF, -1))
        .flatMap(lines -> parseLines(lines, element, context));
  }

  private boolean startsWithOkOrErrOrSpace(RbelContent array) {
    return array.startsWith(OK_SPACE_PREFIX)
        || array.startsWith(OK_PREFIX)
        || array.startsWith(ERR_PREFIX)
        || array.startsWith(SPACE_PREFIX);
  }

  private Optional<String> getCompleteResponse(
      RbelElement element, final RbelConversionExecutor context) {
    log.debug(
        "getCompleteResponse: {}",
        bytesToStringWithoutNonPrintableCharacters(element.getContent().toByteArray()));
    int endIndex;
    int firstLineEnd = element.getContent().indexOf(EmailConversionUtils.CRLF_BYTES);
    if (firstLineEnd < 0) {
      return Optional.empty();
    }
    if (isFirstResponse(element, context)) {
      endIndex = firstLineEnd + EmailConversionUtils.CRLF_BYTES.length;
    } else {
      Pair<byte[], Integer> indexBytesAndIndex =
          findPop3Command(element, context)
              .map(
                  command -> {
                    log.debug("found command: {}", command);
                    return switch (command) {
                      case TOP, CAPA, LIST, RETR, UIDL -> Pair.of(
                          CRLF_DOT_CRLF_BYTES,
                          element.getContent().indexOf(CRLF_DOT_CRLF_BYTES, firstLineEnd));
                      case AUTH -> Pair.of(
                          EmailConversionUtils.CRLF_BYTES, findAuthLinesEndIndex(element));
                      default -> null;
                    };
                  })
              .orElse(Pair.of(EmailConversionUtils.CRLF_BYTES, firstLineEnd));
      endIndex = indexBytesAndIndex.getRight();
      if (endIndex < 0) {
        return Optional.empty();
      } else {
        endIndex += indexBytesAndIndex.getLeft().length;
      }
    }
    String result = new String(element.getContent().subArray(0, endIndex), StandardCharsets.UTF_8);
    log.debug("result: {}", result);
    return Optional.of(result);
  }

  private static int findAuthLinesEndIndex(RbelElement element) {
    int index = 0;
    while (element.getContent().startsWith(SPACE_PREFIX, index)) {
      index = element.getContent().indexOf(EmailConversionUtils.CRLF_BYTES, index);
      if (index >= 0) {
        index += EmailConversionUtils.CRLF_BYTES.length;
      } else {
        return index;
      }
    }
    return element.getContent().indexOf(EmailConversionUtils.CRLF_BYTES, index);
  }

  private static boolean isFirstResponse(RbelElement element, RbelConversionExecutor context) {
    return context
        .findPreviousMessageInSameConnectionAs(
            element, e -> e.hasFacet(RbelPop3ResponseFacet.class))
        .isEmpty();
  }

  private Optional<Pair<RbelPop3ResponseFacet, Integer>> parseLines(
      String[] lines, RbelElement element, RbelConversionExecutor context) {
    var lastHeaderLine = findLastHeaderLineIndex(lines);
    if (lastHeaderLine == lines.length) {
      return Optional.empty();
    }

    String[] firstLineParts = lines[lastHeaderLine].split(" ", 2);
    String status = firstLineParts[lastHeaderLine];
    String header = firstLineParts.length > 1 ? firstLineParts[1] : null;
    if (header == null || header.isBlank()) {
      return Optional.of(buildResponseFacet(element, status, null, lines, lastHeaderLine + 1));
    } else {
      return buildHeaderElement(element, header, context)
          .map(
              headerElement ->
                  buildResponseFacet(element, status, headerElement, lines, lastHeaderLine + 1));
    }
  }

  private static int findLastHeaderLineIndex(String[] lines) {
    var lastHeaderLine = 0;
    while (lastHeaderLine < lines.length) {
      if (lines[lastHeaderLine].startsWith("+ ")) {
        lastHeaderLine++;
      } else {
        break;
      }
    }
    return lastHeaderLine;
  }

  private Pair<RbelPop3ResponseFacet, Integer> buildResponseFacet(
      RbelElement element,
      String status,
      RbelElement headerElement,
      String[] lines,
      int firstBodyLine) {
    var length = Stream.of(lines).mapToInt(String::length).sum() + 2 * (lines.length - 1);
    var response =
        RbelPop3ResponseFacet.builder()
            .status(EmailConversionUtils.createChildElement(element, status))
            .header(headerElement)
            .body(EmailConversionUtils.parseMailBody(element, lines, firstBodyLine))
            .build();
    return Pair.of(response, length);
  }

  private Optional<RbelElement> buildHeaderElement(
      RbelElement element, String header, RbelConversionExecutor context) {
    if (isFirstResponse(element, context)) {
      return Optional.of(EmailConversionUtils.createChildElement(element, header));
    }

    return findPop3Command(element, context)
        .map(
            command ->
                switch (command) {
                  case LIST, STAT -> buildStatOrListElement(element, header);
                  default -> Optional.of(EmailConversionUtils.createChildElement(element, header));
                })
        .orElse(Optional.of(EmailConversionUtils.createChildElement(element, header)));
  }

  private static Optional<RbelElement> findPop3Request(
      RbelElement element, RbelConversionExecutor context) {
    return findAndPairMatchingRequest(element, context, RbelPop3CommandFacet.class);
  }

  private static Optional<RbelPop3Command> findPop3Command(
      RbelElement element, RbelConversionExecutor context) {
    return findPop3Request(element, context).flatMap(RbelPop3ResponseConverter::getPop3Command);
  }

  private static Optional<RbelPop3Command> getPop3Command(RbelElement element) {
    return element
        .getFacet(RbelPop3CommandFacet.class)
        .map(RbelPop3CommandFacet::getCommand)
        .flatMap(e -> e.seekValue(RbelPop3Command.class));
  }

  private Optional<RbelElement> buildStatOrListElement(RbelElement element, String header) {
    var matcher = STAT_OR_LIST_HEADER.matcher(header);
    if (matcher.matches()) {
      var size = Optional.ofNullable(matcher.group("size")).orElse(matcher.group("size2"));
      var count = matcher.group("count");
      final RbelElement headerElement = RbelElement.wrap(element, header);
      return Optional.of(
          headerElement.addFacet(
              RbelPop3StatOrListHeaderFacet.builder()
                  .count(RbelElement.wrap(headerElement, count))
                  .size(
                      Optional.ofNullable(size)
                          .map(s -> RbelElement.wrap(headerElement, s))
                          .orElse(null))
                  .build()));
    }
    return Optional.empty();
  }
}
