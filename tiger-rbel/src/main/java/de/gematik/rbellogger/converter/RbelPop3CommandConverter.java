/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@ConverterInfo(onlyActivateFor = "pop3")
@Slf4j
public class RbelPop3CommandConverter implements RbelConverterPlugin {

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildPop3CommandFacet(element)
        .ifPresent(
            facet -> {
              element.addFacet(facet);
              element.addFacet(new RbelRequestFacet(facet.getCommand().getRawStringContent()));
            });
  }

  private Optional<RbelPop3CommandFacet> buildPop3CommandFacet(RbelElement element) {
    return Optional.ofNullable(element.getRawContent())
        .filter(c -> c.length > 4)
        .filter(EmailConversionUtils::endsWithCrLf)
        .flatMap(this::parseCommand)
        .map(command -> getRbelPop3CommandFacet(element, command));
  }

  private RbelPop3CommandFacet getRbelPop3CommandFacet(
      RbelElement element, RbelPop3Command command) {
    var commandBytes = command.name().getBytes(StandardCharsets.UTF_8);
    return RbelPop3CommandFacet.builder()
        .command(RbelElement.wrap(commandBytes, element, command))
        .arguments(parseArguments(element, commandBytes.length + 1))
        .build();
  }

  private Optional<RbelPop3Command> parseCommand(byte[] c) {
    var shortPrefix =
        new String(
            ArrayUtils.subarray(c, 0, RbelPop3Command.MAX_LENGTH + 1), StandardCharsets.UTF_8);
    var command = new StringTokenizer(shortPrefix).nextToken();
    try {
      return Optional.of(RbelPop3Command.valueOf(command));
    } catch (IllegalArgumentException e) {
      // fall through
    }
    return Optional.empty();
  }

  private RbelElement parseArguments(RbelElement element, int argumentsOffset) {
    var rawContent = element.getRawContent();
    if (rawContent.length > argumentsOffset + 2) {
      var argumentBytes = ArrayUtils.subarray(rawContent, argumentsOffset, rawContent.length - 2);
      return new RbelElement(argumentBytes, element);
    } else {
      return null;
    }
  }
}
