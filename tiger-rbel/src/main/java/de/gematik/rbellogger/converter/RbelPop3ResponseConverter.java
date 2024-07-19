/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;
import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF_DOT_CRLF;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@ConverterInfo(onlyActivateFor = "pop3")
@Slf4j
public class RbelPop3ResponseConverter implements RbelConverterPlugin {

  private static final Pattern STAT_OR_LIST_HEADER =
      Pattern.compile("(?<count>\\d+) ((?<size>\\d+)|messages \\((?<size2>\\d+) octets\\))");

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildPop3ResponseFacet(element, context)
        .ifPresent(
            facet -> {
              element.addFacet(facet);
              element.addFacet(new RbelResponseFacet(facet.getStatus().getRawStringContent()));
              Optional.ofNullable(facet.getBody())
                  .filter(
                      body ->
                          findPop3Command(element, context)
                              .filter(RbelPop3Command.RETR::equals)
                              .isPresent())
                  .ifPresent(context::convertElement);
            });
  }

  private Optional<RbelPop3ResponseFacet> buildPop3ResponseFacet(
      RbelElement element, final RbelConverter context) {
    return Optional.ofNullable(element.getRawContent())
        .filter(c -> c.length > 4)
        .filter(this::startsWithOkOrErr)
        .filter(EmailConversionUtils::endsWithCrLf)
        .map(c -> new String(c, StandardCharsets.UTF_8))
        .filter(this::isCompleteResponse)
        .map(s -> s.split(CRLF, -1))
        .flatMap(lines -> parseLines(lines, element, context));
  }

  private boolean startsWithOkOrErr(byte[] c) {
    return (c[0] == '+'
            && c[1] == 'O'
            && c[2] == 'K'
            && (c[3] == ' ' || (c[3] == '\r' && c[4] == '\n')))
        || (c[0] == '-' && c[1] == 'E' && c[2] == 'R' && c[3] == 'R' && c[4] == ' ');
  }

  private boolean isCompleteResponse(String response) {
    return response.endsWith(CRLF_DOT_CRLF) || response.indexOf(CRLF) == response.length() - 2;
  }

  private Optional<RbelPop3ResponseFacet> parseLines(
      String[] lines, RbelElement element, RbelConverter context) {
    String[] firstLineParts = lines[0].split(" ", 2);
    String status = firstLineParts[0];
    String header = firstLineParts.length > 1 ? firstLineParts[1] : null;
    context.waitForAllElementsBeforeGivenToBeParsed(element.findRootElement());
    if (header == null) {
      return findPop3Command(element, context)
          .flatMap(
              command ->
                  switch (command) {
                    case CAPA, RETR -> lines.length < 3
                        ? Optional.empty()
                        : Optional.of(buildResponseFacet(element, status, null, lines));
                    case USER, PASS -> lines.length > 2
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

  private Optional<RbelPop3Command> findPop3Command(RbelElement element, RbelConverter context) {
    return context
        .messagesStreamLatestFirst()
        .filter(e -> element != e)
        .filter(e -> e.hasFacet(RbelPop3CommandFacet.class))
        .filter(e -> matchesSenderAndReceiver(e, element))
        .findFirst()
        .flatMap(this::getPop3Command);
  }

  private boolean matchesSenderAndReceiver(RbelElement pop3Command, RbelElement pop3Response) {
    return pop3Command
        .getFacet(RbelTcpIpMessageFacet.class)
        .filter(
            request ->
                pop3Response
                    .getFacet(RbelTcpIpMessageFacet.class)
                    .filter(
                        response ->
                            equalAddresses(request.getSender(), response.getReceiver())
                                && equalAddresses(request.getReceiver(), response.getSender()))
                    .isPresent())
        .isPresent();
  }

  private static boolean equalAddresses(RbelElement a1, RbelElement a2) {
    return a1.getFacet(RbelHostnameFacet.class)
        .filter(
            host1 ->
                a2.getFacet(RbelHostnameFacet.class)
                    .filter(host2 -> equalValues(host1.getDomain(), host2.getDomain()))
                    .filter(host2 -> equalValues(host1.getPort(), host2.getPort()))
                    .isPresent())
        .isPresent();
  }

  private static boolean equalValues(RbelElement e1, RbelElement e2) {
    return e1.seekValue()
        .filter(v1 -> e2.seekValue().filter(v2 -> v1.equals(v2)).isPresent())
        .isPresent();
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
}
