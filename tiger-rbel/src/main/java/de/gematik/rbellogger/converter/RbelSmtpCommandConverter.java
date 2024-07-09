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

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;
import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF_DOT_CRLF;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.data.smtp.RbelSmtpCommand;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class RbelSmtpCommandConverter implements RbelConverterPlugin {

  public static final int MIN_SMTP_COMMAND_LINE_LENGTH = 6;

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    buildSmtpCommandFacet(element)
        .ifPresent(
            facet -> {
              element.addFacet(facet);
              Optional.ofNullable(facet.getBody()).ifPresent(context::convertElement);
              element.addFacet(new RbelRootFacet<>(facet));
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
    return command.startsWith("DATA\r\n")
        ? command.endsWith(CRLF_DOT_CRLF)
        : command.indexOf(CRLF) == command.length() - 2;
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
    return RbelSmtpCommandFacet.builder()
        .command(
            RbelElement.wrap(command.name().getBytes(StandardCharsets.UTF_8), element, command))
        .arguments(parseArguments(lines[0], element))
        .body(EmailConversionUtils.parseMailBody(element, lines))
        .build();
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
