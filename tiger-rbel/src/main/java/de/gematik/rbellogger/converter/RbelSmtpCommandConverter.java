/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;
import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF_DOT_CRLF;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.smtp.RbelSmtpCommand;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
@ConverterInfo(onlyActivateFor = "smtp")
public class RbelSmtpCommandConverter implements RbelConverterPlugin {

  public static final int MIN_SMTP_COMMAND_LINE_LENGTH = 6;

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildSmtpCommandFacet(element)
        .ifPresent(
            facet -> {
              element.addFacet(facet);
              facet
                  .getCommand()
                  .seekValue(RbelSmtpCommand.class)
                  .filter(RbelSmtpCommand.DATA::equals)
                  .map(command -> facet.getBody())
                  .ifPresent(context::convertElement);
              element.addFacet(new RbelRootFacet<>(facet));
              element.addFacet(new RbelRequestFacet(facet.getCommand().getRawStringContent()));
            });
  }

  private Optional<RbelSmtpCommandFacet> buildSmtpCommandFacet(RbelElement element) {
    return Optional.ofNullable(element.getRawContent())
        .filter(c -> c.length >= MIN_SMTP_COMMAND_LINE_LENGTH)
        .filter(EmailConversionUtils::endsWithCrLf)
        .flatMap(this::parseCommand)
        .flatMap(
            command ->
                Optional.of(element.getRawContent())
                    .map(c -> new String(c, StandardCharsets.UTF_8))
                    .filter(this::isCompleteCommand)
                    .map(s -> buildSmtpCommandFacet(command, s, element)));
  }

  private boolean isCompleteCommand(String command) {
    if (command.startsWith("DATA\r\n")) {
      return command.endsWith(CRLF_DOT_CRLF);
    } else if (command.startsWith("AUTH ") && !command.startsWith("AUTH PLAIN")) {
      // AUTH (without PLAIN) needs another 2 lines with the credentials
      return command.split(CRLF).length == 3;
    } else {
      return command.indexOf(CRLF) == command.length() - CRLF.length();
    }
  }

  private Optional<RbelSmtpCommand> parseCommand(byte[] c) {
    var shortPrefix =
        new String(ArrayUtils.subarray(c, 0, MIN_SMTP_COMMAND_LINE_LENGTH), StandardCharsets.UTF_8);
    var command = new StringTokenizer(shortPrefix).nextToken();
    try {
      return Optional.of(RbelSmtpCommand.valueOf(command));
    } catch (IllegalArgumentException e) {
      // fall through
    }
    return Optional.empty();
  }

  private RbelSmtpCommandFacet buildSmtpCommandFacet(
      RbelSmtpCommand command, String content, RbelElement element) {
    String[] lines = content.split(CRLF, -1);
    RbelElement body = buildSmtpBody(command, element, lines);
    return RbelSmtpCommandFacet.builder()
        .command(
            RbelElement.wrap(command.name().getBytes(StandardCharsets.UTF_8), element, command))
        .arguments(parseArguments(lines[0], element))
        .body(body)
        .build();
  }

  private RbelElement buildSmtpBody(RbelSmtpCommand command, RbelElement element, String[] lines) {
    return switch (command) {
      case AUTH ->
          lines.length > 2
              ? EmailConversionUtils.createChildElement(
                  element,
                  Arrays.stream(lines)
                      .skip(1)
                      .limit(lines.length - 2L)
                      .collect(Collectors.joining(CRLF)))
              : null;
      case DATA -> EmailConversionUtils.parseMailBody(element, lines);
      default -> null;
    };
  }

  private static RbelElement parseArguments(String line, RbelElement element) {
    String[] firstLineParts = line.split(" ", 2);
    RbelElement argumentsElement = null;
    if (firstLineParts.length > 1) {
      argumentsElement = EmailConversionUtils.createChildElement(element, firstLineParts[1]);
    }
    return argumentsElement;
  }
}
