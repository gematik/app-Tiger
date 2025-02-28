/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelPop3CommandFacet;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.data.facet.RbelPop3StatOrListHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelResponseFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import de.gematik.rbellogger.util.EmailConversionUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@ConverterInfo(onlyActivateFor = "pop3")
@Slf4j
public class RbelPop3ResponseConverter implements RbelConverterPlugin {

  private static final Pattern STAT_OR_LIST_HEADER =
      Pattern.compile("(?<count>\\d+) ((?<size>\\d+)|messages(:| \\((?<size2>\\d+) octets\\)))");

  private static final Set<RbelPop3Command> MIME_BODY_RESPONSE_COMMANDS =
      Set.of(RbelPop3Command.RETR, RbelPop3Command.TOP);
  private static final byte[] CRLF_DOT_CRLF_BYTES = EmailConversionUtils.CRLF_DOT_CRLF.getBytes();

  @Override
  public boolean ignoreOversize() {
    return true;
  }

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildPop3ResponseFacet(element, context)
        .ifPresentOrElse(
            facet -> {
              element.addFacet(facet);
              element.addFacet(new RbelResponseFacet(facet.getStatus().getRawStringContent()));
              Optional.ofNullable(facet.getBody())
                  .filter(
                      body ->
                          findPop3Command(element, context)
                              .filter(MIME_BODY_RESPONSE_COMMANDS::contains)
                              .isPresent())
                  .ifPresent(context::convertElement);
              findPop3Request(element, context)
                  .ifPresent(
                      request -> {
                        request.removeFacetsOfType(TigerNonPairedMessageFacet.class);
                        element.removeFacetsOfType(TigerNonPairedMessageFacet.class);
                      });
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

  private Optional<RbelPop3ResponseFacet> buildPop3ResponseFacet(
      RbelElement element, final RbelConverter context) {
    return Optional.of(element.getContent())
        .filter(c -> c.size() > 4)
        .filter(this::startsWithOkOrErr)
        .filter(EmailConversionUtils::endsWithCrLf)
        .filter(this::isCompleteResponse)
        .map(c -> new String(c.toByteArray(), StandardCharsets.UTF_8))
        .map(s -> s.split(EmailConversionUtils.CRLF, -1))
        .flatMap(lines -> parseLines(lines, element, context));
  }

  private boolean startsWithOkOrErr(RbelContent array) {
    var c = array.subArray(0, 5);
    return (c[0] == '+'
            && c[1] == 'O'
            && c[2] == 'K'
            && (c[3] == ' ' || (c[3] == '\r' && c[4] == '\n')))
        || (c[0] == '-' && c[1] == 'E' && c[2] == 'R' && c[3] == 'R' && c[4] == ' ');
  }

  private boolean isCompleteResponse(RbelContent content) {
    return content.endsWith(CRLF_DOT_CRLF_BYTES)
        || EmailConversionUtils.hasCompleteLines(content, 1);
  }

  private Optional<RbelPop3ResponseFacet> parseLines(
      String[] lines, RbelElement element, RbelConverter context) {
    String[] firstLineParts = lines[0].split(" ", 2);
    String status = firstLineParts[0];
    String header = firstLineParts.length > 1 ? firstLineParts[1] : null;
    if (header == null || header.isBlank()) {
      return findPop3Command(element, context)
          .flatMap(
              command ->
                  switch (command) {
                    case CAPA, RETR, TOP, LIST, UIDL ->
                        lines.length < 3
                            ? Optional.empty()
                            : Optional.of(buildResponseFacet(element, status, null, lines));
                    case USER, PASS, NOOP ->
                        lines.length > 2
                            ? Optional.empty()
                            : Optional.of(buildResponseFacet(element, status, null, lines));
                    default -> Optional.empty();
                  });
    }
    return buildHeaderElement(element, header, context)
        .map(headerElement -> buildResponseFacet(element, status, headerElement, lines));
  }

  private RbelPop3ResponseFacet buildResponseFacet(
      RbelElement element, String status, RbelElement headerElement, String[] lines) {
    return RbelPop3ResponseFacet.builder()
        .status(EmailConversionUtils.createChildElement(element, status))
        .header(headerElement)
        .body(EmailConversionUtils.parseMailBody(element, lines))
        .build();
  }

  private Optional<RbelElement> buildHeaderElement(
      RbelElement element, String header, RbelConverter context) {
    return findPop3Command(element, context)
        .map(
            command ->
                switch (command) {
                  case LIST, STAT -> buildStatOrListElement(element, header);
                  default -> Optional.of(EmailConversionUtils.createChildElement(element, header));
                })
        .orElse(Optional.of(EmailConversionUtils.createChildElement(element, header)));
  }

  private Optional<RbelElement> findPop3Request(RbelElement element, RbelConverter context) {
    return RbelTcpIpMessageFacet.findAndPairMatchingRequest(
        element, context, RbelPop3CommandFacet.class);
  }

  private Optional<RbelPop3Command> findPop3Command(RbelElement element, RbelConverter context) {
    return findPop3Request(element, context).flatMap(this::getPop3Command);
  }

  private Optional<RbelPop3Command> getPop3Command(RbelElement element) {
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
