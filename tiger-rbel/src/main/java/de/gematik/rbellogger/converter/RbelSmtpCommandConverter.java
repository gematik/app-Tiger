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
import de.gematik.rbellogger.data.facet.RbelRequestFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelSmtpCommandFacet;
import de.gematik.rbellogger.data.smtp.RbelSmtpCommand;
import de.gematik.rbellogger.util.EmailConversionUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConverterInfo(onlyActivateFor = "smtp")
public class RbelSmtpCommandConverter implements RbelConverterPlugin {

  public static final int MIN_SMTP_COMMAND_LINE_LENGTH = 6;
  private static final byte[] CRLF_DOT_CRLF_BYTES = EmailConversionUtils.CRLF_DOT_CRLF.getBytes();
  private static final byte[] AUTH_COMMAND_PREFIX_BYTES = "AUTH ".getBytes();
  private static final byte[] AUTH_PLAIN_PREFIX_BYTES = "AUTH PLAIN".getBytes();
  private static final byte[] DATA_PREFIX_BYTES = "DATA\r\n".getBytes();

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
              element.addFacet(
                  RbelRequestFacet.builder()
                      .responseRequired(true)
                      .menuInfoString(facet.getCommand().getRawStringContent())
                      .build());
            });
  }

  private Optional<RbelSmtpCommandFacet> buildSmtpCommandFacet(RbelElement element) {
    return Optional.of(element.getContent())
        .filter(c -> c.size() >= MIN_SMTP_COMMAND_LINE_LENGTH)
        .filter(EmailConversionUtils::endsWithCrLf)
        .flatMap(this::parseCommand)
        .flatMap(
            command ->
                Optional.of(element.getContent())
                    .filter(this::isCompleteCommand)
                    .map(c -> new String(c.toByteArray(), StandardCharsets.UTF_8))
                    .map(s -> buildSmtpCommandFacet(command, s, element)));
  }

  private boolean isCompleteCommand(RbelContent content) {
    if (content.startsWith(DATA_PREFIX_BYTES)) {
      return content.endsWith(CRLF_DOT_CRLF_BYTES);
    } else if (content.startsWith(AUTH_COMMAND_PREFIX_BYTES)
        && !content.startsWith(AUTH_PLAIN_PREFIX_BYTES)) {
      // AUTH (without PLAIN) needs another 2 lines with the credentials
      return EmailConversionUtils.hasCompleteLines(content, 3);
    } else {
      return EmailConversionUtils.hasCompleteLines(content, 1);
    }
  }

  private Optional<RbelSmtpCommand> parseCommand(RbelContent content) {
    var shortPrefix =
        new String(content.subArray(0, MIN_SMTP_COMMAND_LINE_LENGTH), StandardCharsets.UTF_8);
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
    String[] lines = content.split(EmailConversionUtils.CRLF, -1);
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
                      .collect(Collectors.joining(EmailConversionUtils.CRLF)))
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
