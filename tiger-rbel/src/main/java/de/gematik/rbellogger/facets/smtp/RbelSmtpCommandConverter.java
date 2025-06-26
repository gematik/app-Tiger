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
package de.gematik.rbellogger.facets.smtp;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.util.EmailConversionUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@ConverterInfo(onlyActivateFor = "smtp")
public class RbelSmtpCommandConverter extends RbelConverterPlugin {

  public static final int MIN_SMTP_COMMAND_LINE_LENGTH = 6;
  private static final byte[] CRLF_DOT_CRLF_BYTES = EmailConversionUtils.CRLF_DOT_CRLF.getBytes();
  private static final byte[] AUTH_COMMAND_PREFIX_BYTES = "AUTH ".getBytes();
  private static final byte[] AUTH_PLAIN_PREFIX_BYTES = "AUTH PLAIN".getBytes();
  private static final byte[] DATA_PREFIX_BYTES = "DATA\r\n".getBytes();

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    buildSmtpCommandFacet(element)
        .ifPresent(
            pair -> {
              var facet = pair.getLeft();
              var length = pair.getRight();
              element.addFacet(facet);
              element.setUsedBytes(length);

              element.addFacet(new RbelRootFacet<>(facet));
              element.addFacet(
                  new RbelRequestFacet(facet.getCommand().getRawStringContent(), true));
            });
  }

  public static class RbelSmtpBodyConverter extends RbelConverterPlugin {
    @Override
    public void consumeElement(RbelElement element, RbelConversionExecutor context) {
      element
          .getFacet(RbelSmtpCommandFacet.class)
          .filter(facet -> facet.getCommand().seekValue().get().equals(RbelSmtpCommand.DATA))
          .map(RbelSmtpCommandFacet::getBody)
          .ifPresent(context::convertElement);
    }
  }

  private Optional<Pair<RbelSmtpCommandFacet, Integer>> buildSmtpCommandFacet(RbelElement element) {
    return Optional.of(element.getContent())
        .filter(c -> c.size() >= MIN_SMTP_COMMAND_LINE_LENGTH)
        .flatMap(this::parseCommand)
        .flatMap(
            command ->
                Optional.of(element.getContent())
                    .flatMap(this::getCompleteCommandContent)
                    .map(
                        content ->
                            Pair.of(
                                buildSmtpCommandFacet(command, content, element), content.size())));
  }

  public Optional<RbelContent> findEndOfLine(RbelContent content, int numberOfLines) {
    int endIndex = 0;
    for (int i = 0; i < numberOfLines; i++) {
      endIndex = content.indexOf(EmailConversionUtils.CRLF_BYTES, endIndex);
      if (endIndex < 0) {
        return Optional.empty();
      }
      endIndex += EmailConversionUtils.CRLF_BYTES.length;
    }
    return Optional.of(content.subArray(0, endIndex));
  }

  private Optional<RbelContent> getCompleteCommandContent(RbelContent content) {
    if (content.startsTrimmedWithIgnoreCase(DATA_PREFIX_BYTES, StandardCharsets.UTF_8)) {
      var endIndex = content.indexOf(CRLF_DOT_CRLF_BYTES);
      if (endIndex >= 0) {
        return Optional.of(content.subArray(0, endIndex + CRLF_DOT_CRLF_BYTES.length));
      }
      return Optional.empty();
    } else if (content.startsTrimmedWithIgnoreCase(
            AUTH_COMMAND_PREFIX_BYTES, StandardCharsets.UTF_8)
        && !content.startsTrimmedWithIgnoreCase(AUTH_PLAIN_PREFIX_BYTES, StandardCharsets.UTF_8)) {
      // AUTH (without PLAIN) needs another 2 lines with the credentials
      return findEndOfLine(content, 3);
    } else {
      return findEndOfLine(content, 1);
    }
  }

  private Optional<RbelSmtpCommand> parseCommand(RbelContent content) {
    var shortPrefix =
        new String(content.toByteArray(0, MIN_SMTP_COMMAND_LINE_LENGTH), StandardCharsets.UTF_8);
    var command = new StringTokenizer(shortPrefix).nextToken();
    try {
      return Optional.of(RbelSmtpCommand.fromStringIgnoringCase(command));
    } catch (IllegalArgumentException e) {
      // fall through
    }
    return Optional.empty();
  }

  private RbelSmtpCommandFacet buildSmtpCommandFacet(
      RbelSmtpCommand command, RbelContent content, RbelElement element) {
    var lines = content.split(EmailConversionUtils.CRLF_BYTES);
    RbelElement body = buildSmtpBody(command, element, lines);
    return RbelSmtpCommandFacet.builder()
        .command(
            RbelElement.wrap(command.name().getBytes(StandardCharsets.UTF_8), element, command))
        .arguments(parseArguments(lines.get(0), element))
        .body(body)
        .build();
  }

  private RbelElement buildSmtpBody(
      RbelSmtpCommand command, RbelElement element, List<RbelContent> lines) {
    return switch (command) {
      case AUTH -> lines.size() > 2
          ? EmailConversionUtils.createChildElement(
              element,
              new String(
                  EmailConversionUtils.mergeLines(lines.subList(1, lines.size())).toByteArray(),
                  StandardCharsets.UTF_8))
          : null;
      case DATA -> EmailConversionUtils.parseMailBody(element, lines, 1);
      default -> null;
    };
  }

  private static RbelElement parseArguments(RbelContent line, RbelElement element) {
    String[] firstLineParts =
        new String(
                line.toByteArray(0, line.size() - EmailConversionUtils.CRLF_BYTES.length),
                StandardCharsets.UTF_8)
            .split(" ", 2);
    RbelElement argumentsElement = null;
    if (firstLineParts.length > 1) {
      argumentsElement = EmailConversionUtils.createChildElement(element, firstLineParts[1]);
    }
    return argumentsElement;
  }
}
