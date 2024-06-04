/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelPop3CommandFacet;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.data.facet.RbelPop3StatOrListHeaderFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import de.gematik.rbellogger.util.Pop3Utils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelPop3ResponseConverter implements RbelConverterPlugin {

  private static final Pattern STAT_OR_LIST_HEADER =
      Pattern.compile("(?<count>\\d+) ((?<size>\\d+)|messages \\((?<size2>\\d+) octets\\))");

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildPop3ResponseFacet(element, context).ifPresent(element::addFacet);
  }

  private Optional<RbelPop3ResponseFacet> buildPop3ResponseFacet(
      RbelElement element, final RbelConverter context) {
    return Optional.ofNullable(element.getRawContent())
        .filter(c -> c.length > 5)
        .filter(this::startsWithOkOrErr)
        .filter(Pop3Utils::endsWithCrLf)
        .map(c -> new String(c, StandardCharsets.UTF_8))
        .filter(
            s ->
                s.endsWith(Pop3Utils.CRLF + "." + Pop3Utils.CRLF)
                    || s.indexOf(Pop3Utils.CRLF) == s.length() - 2)
        .map(s -> s.split(Pop3Utils.CRLF, -1))
        .flatMap(lines -> parseLines(lines, element, context));
  }

  private boolean startsWithOkOrErr(byte[] c) {
    return (c[0] == '+' && c[1] == 'O' && c[2] == 'K' && c[3] == ' ')
        || (c[0] == '-' && c[1] == 'E' && c[2] == 'R' && c[3] == 'R' && c[4] == ' ');
  }

  private Optional<RbelPop3ResponseFacet> parseLines(
      String[] lines, RbelElement element, RbelConverter context) {
    int indexOfSpace = lines[0].indexOf(" ");
    String status = lines[0].substring(0, indexOfSpace);
    String header = lines[0].substring(indexOfSpace + 1);
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    return buildHeaderElement(element, header)
        .map(
            headerElement ->
                RbelPop3ResponseFacet.builder()
                    .status(Pop3Utils.createChildElement(element, status))
                    .header(headerElement)
                    .body(buildBodyElement(element, context, lines))
                    .build());
  }

  private Optional<RbelElement> buildHeaderElement(RbelElement element, String header) {
    return element
        .findRootElement()
        .getFacet(TracingMessagePairFacet.class)
        .map(TracingMessagePairFacet::getRequest)
        .flatMap(request -> request.getFacet(RbelPop3CommandFacet.class))
        .map(RbelPop3CommandFacet::getCommand)
        .flatMap(e -> e.seekValue(RbelPop3Command.class))
        .map(
            command ->
                switch (command) {
                  case LIST, STAT -> buildStatOrListElement(element, header);
                  default -> Optional.<RbelElement>empty();
                })
        .orElse(Optional.of(Pop3Utils.createChildElement(element, header)));
  }

  private Optional<RbelElement> buildStatOrListElement(RbelElement element, String header) {
    var matcher = STAT_OR_LIST_HEADER.matcher(header);
    if (matcher.matches()) {
      var size = Optional.ofNullable(matcher.group("size")).orElse(matcher.group("size2"));
      var count = matcher.group("count");
      return Optional.of(
          RbelElement.wrap(element, header)
              .addFacet(
                  RbelPop3StatOrListHeaderFacet.builder()
                      .count(RbelElement.wrap(element, count))
                      .size(RbelElement.wrap(element, size))
                      .build()));
    }
    return Optional.empty();
  }

  private RbelElement buildBodyElement(RbelElement element, RbelConverter context, String[] lines) {
    if (lines.length > 2) {
      var body = extractBodyAndRemoveStuffedDots(lines);
      return context.convertElement(Pop3Utils.createChildElement(element, body));
    }
    return null;
  }

  private String extractBodyAndRemoveStuffedDots(String[] lines) {
    return Arrays.asList(lines).subList(1, lines.length - 2).stream()
        .map(Pop3Utils::removeStuffedDot)
        .collect(Collectors.joining(Pop3Utils.CRLF));
  }
}
