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
package de.gematik.rbellogger;

import static de.gematik.rbellogger.RbelConversionPhase.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/** Executor for complete parsing of a message through all phases. */
@RequiredArgsConstructor
@Accessors(chain = true)
@Slf4j
public class RbelConversionExecutor {

  @Getter private final RbelConverter converter;
  private final RbelElement rootElement;
  private final int skipParsingWhenMessageLargerThanKb;
  @Getter private final RbelKeyManager rbelKeyManager;
  @Getter private RbelConversionPhase conversionPhase;
  private final List<RbelConversionPhase> toBeConsideredPhases;
  private boolean messageWasDeleted = false;

  public RbelElement execute() {
    long timeBeforeConversion = System.nanoTime();
    try {
      for (RbelConversionPhase phase : toBeConsideredPhases) {
        if (rootElement.getParentNode() == null) {
          rootElement.setConversionPhase(phase);
        }
        conversionPhase = phase;

        executeConversionPhase(phase);

        if (messageWasDeleted) {
          rootElement.setConversionPhase(DELETION);
          executeConversionPhase(DELETION);
          rootElement.setConversionPhase(DELETED);
          converter.signalMessageParsingIsComplete(rootElement);
          return rootElement;
        }
      }
      rootElement.setConversionPhase(COMPLETED);
      converter.signalMessageParsingIsComplete(rootElement);
      return rootElement;
    } finally {
      log.atTrace()
          .addArgument(rootElement::getUuid)
          .addArgument(converter::getName)
          .addArgument(rootElement::getConversionPhase)
          .log("Finalizing conversion of message {} in proxy {}, final phase is {}");
      if (!messageWasDeleted) {
        long timeAfterConversion = System.nanoTime();
        rootElement.setConversionTimeInNanos(timeAfterConversion - timeBeforeConversion);
      }
    }
  }

  public RbelElement convertElement(final RbelElement convertedInput) {
    return converter.convertElement(convertedInput);
  }

  /** Triggers conversion FOR THE GIVEN PHASE ONLY for the root element. */
  private void executeConversionPhase(RbelConversionPhase conversionPhase) {
    boolean elementIsOversized =
        skipParsingWhenMessageLargerThanKb > -1
            && (rootElement.getSize() > skipParsingWhenMessageLargerThanKb * 1024L);
    for (RbelConverterPlugin plugin : converter.getConverterPlugins().get(conversionPhase)) {
      if (conversionPhase == RbelConversionPhase.CONTENT_PARSING
          && elementIsOversized
          && plugin.skipParsingOversizedContent()) {
        continue;
      }
      try {
        plugin.doConversionIfActive(rootElement, this);
      } catch (RuntimeException e) {
        val conversionException =
            RbelConversionException.wrapIfNotAConversionException(e, plugin, rootElement);
        conversionException.printDetailsToLog(log);
        conversionException.addErrorNoteFacetToElement();
      }
      if (messageWasDeleted && conversionPhase != RbelConversionPhase.DELETION) {
        return;
      }
    }
  }

  public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).rawContent(input).build());
  }

  public RbelElement convertElement(RbelContent input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).content(input).build());
  }

  public RbelElement convertElement(final String input, RbelElement parentNode) {
    return convertElement(
        RbelElement.builder()
            .parentNode(parentNode)
            .rawContent(
                input.getBytes(
                    Optional.ofNullable(parentNode)
                        .map(RbelElement::getElementCharset)
                        .orElse(StandardCharsets.UTF_8)))
            .build());
  }

  public void waitForAllElementsBeforeGivenToBeParsed(RbelElement rootElement) {
    converter.waitForAllElementsBeforeGivenToBeParsed(rootElement);
  }

  public Stream<RbelElement> messagesStreamLatestFirst() {
    return converter.messagesStreamLatestFirst();
  }

  public Optional<RbelElement> findMessageByUuid(String uuid) {
    return converter.findMessageByUuid(uuid);
  }

  public void removeMessage(RbelElement message) {
    if (message == this.rootElement) {
      messageWasDeleted = true;
    }
    converter.removeMessage(message);
  }

  public boolean isActivateRbelParsing() {
    return converter.isActivateRbelParsing();
  }

  private boolean haveSameConnection(RbelElement element1, RbelElement element2) {
    Optional<RbelTcpIpMessageFacet> tcpIpFacet1 = element1.getFacet(RbelTcpIpMessageFacet.class);
    Optional<RbelTcpIpMessageFacet> tcpIpFacet2 = element2.getFacet(RbelTcpIpMessageFacet.class);
    if (tcpIpFacet1.isEmpty() || tcpIpFacet2.isEmpty()) {
      return false;
    }
    var sender1 = tcpIpFacet1.get().getSenderHostname().orElse(null);
    var sender2 = tcpIpFacet2.get().getSenderHostname().orElse(null);
    var receiver1 = tcpIpFacet1.get().getReceiverHostname().orElse(null);
    var receiver2 = tcpIpFacet2.get().getReceiverHostname().orElse(null);
    return (Objects.equals(sender1, sender2) && Objects.equals(receiver1, receiver2))
        || (Objects.equals(sender1, receiver2) && Objects.equals(receiver1, sender2));
  }

  public Optional<RbelElement> findPreviousMessageInSameConnectionAs(
      RbelElement targetElement, Predicate<RbelElement> additionalFilter) {
    var stream = getPreviousMessagesInSameConnectionAs(targetElement);
    return stream.filter(additionalFilter).findFirst();
  }

  public Stream<RbelElement> getPreviousMessagesInSameConnectionAs(RbelElement targetElement) {
    val messageHistoryAsync = converter.getMessageHistoryAsync();
    messageHistoryAsync.setAllowUnparsedMessagesToAppearInFacade(true);
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                messageHistoryAsync.descendingIterator(), Spliterator.ORDERED),
            false)
        .filter(msg -> msg != targetElement)
        .filter(msg -> haveSameConnection(msg, targetElement));
  }

  public String converterName() {
    return converter.getName();
  }
}
