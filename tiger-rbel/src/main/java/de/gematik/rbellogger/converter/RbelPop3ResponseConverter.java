/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.util.Pop3Utils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelPop3ResponseConverter implements RbelConverterPlugin {

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
        .map(lines -> parseLines(lines, element, context));
  }

  private boolean startsWithOkOrErr(byte[] c) {
    return (c[0] == '+' && c[1] == 'O' && c[2] == 'K' && c[3] == ' ')
        || (c[0] == '-' && c[1] == 'E' && c[2] == 'R' && c[3] == 'R' && c[4] == ' ');
  }

  private RbelPop3ResponseFacet parseLines(
      String[] lines, RbelElement element, RbelConverter context) {
    int indexOfSpace = lines[0].indexOf(" ");
    String status = lines[0].substring(0, indexOfSpace);
    String header = lines[0].substring(indexOfSpace + 1);
    return RbelPop3ResponseFacet.builder()
        .status(Pop3Utils.createChildElement(element, status))
        .header(Pop3Utils.createChildElement(element, header))
        .body(buildBodyElement(element, context, lines))
        .build();
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
