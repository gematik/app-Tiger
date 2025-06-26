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

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF_BYTES;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@ConverterInfo(onlyActivateFor = "pop3")
@Slf4j
public class RbelPop3CommandConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    buildPop3CommandFacet(element)
        .ifPresent(
            pair -> {
              var facet = pair.getLeft();
              var length = pair.getRight();
              element.addFacet(facet);
              element.setUsedBytes(length);
              element.addFacet(
                  new RbelRequestFacet(facet.getCommand().getRawStringContent(), true));
            });
  }

  private Optional<Pair<RbelPop3CommandFacet, Integer>> buildPop3CommandFacet(RbelElement element) {
    return Optional.of(element.getContent())
        .filter(c -> c.size() > 4)
        .flatMap(this::parseCommand)
        .flatMap(command -> buildRbelPop3CommandFacet(element, command));
  }

  private Optional<Pair<RbelPop3CommandFacet, Integer>> buildRbelPop3CommandFacet(
      RbelElement element, RbelPop3Command command) {
    var commandBytes = command.name().getBytes(StandardCharsets.UTF_8);
    int commandEndIndex = element.getContent().indexOf(CRLF_BYTES);
    if (commandEndIndex == -1) {
      return Optional.empty();
    }
    if (command == RbelPop3Command.AUTH
        && commandBytes.length + 1 < commandEndIndex
        && new String(
                element.getContent().toByteArray(commandBytes.length + 1, commandEndIndex),
                StandardCharsets.UTF_8)
            .trim()
            .equalsIgnoreCase("PLAIN")) {
      commandEndIndex =
          element.getContent().indexOf(CRLF_BYTES, commandEndIndex + CRLF_BYTES.length);
      if (commandEndIndex == -1) {
        return Optional.empty();
      }
    }
    var length = commandEndIndex + CRLF_BYTES.length;
    return Optional.of(
        Pair.of(
            RbelPop3CommandFacet.builder()
                .command(RbelElement.wrap(commandBytes, element, command))
                .arguments(parseArguments(element, commandBytes.length + 1, commandEndIndex))
                .build(),
            length));
  }

  private Optional<RbelPop3Command> parseCommand(RbelContent c) {
    try {
      var shortPrefix =
          new String(c.toByteArray(0, RbelPop3Command.MAX_LENGTH + 1), StandardCharsets.UTF_8);
      var command = new StringTokenizer(shortPrefix).nextToken();
      return Optional.of(RbelPop3Command.fromStringIgnoringCase(command));
    } catch (IllegalArgumentException | NoSuchElementException | IndexOutOfBoundsException e) {
      return Optional.empty();
    }
  }

  private RbelElement parseArguments(
      RbelElement parentElement, int argumentsOffset, int commandEndIndex) {
    var content = parentElement.getContent();
    if (commandEndIndex > argumentsOffset) {
      var argumentContent = content.subArray(argumentsOffset, commandEndIndex);
      return RbelElement.builder().content(argumentContent).parentNode(parentElement).build();
    } else {
      return null;
    }
  }
}
