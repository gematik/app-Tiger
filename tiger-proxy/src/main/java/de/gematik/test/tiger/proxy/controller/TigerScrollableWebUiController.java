/*
 * Copyright 2025 gematik GmbH
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

package de.gematik.test.tiger.proxy.controller;

import com.google.common.html.HtmlEscapers;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.GetMessagesFilterScrollableDto;
import de.gematik.test.tiger.proxy.data.GetMessagesWithHtmlScrollableDto;
import de.gematik.test.tiger.proxy.data.GetMessagesWithMetaScrollableDto;
import de.gematik.test.tiger.proxy.data.HtmlMessageScrollableDto;
import de.gematik.test.tiger.proxy.data.JexlQueryResponseScrollableDto;
import de.gematik.test.tiger.proxy.data.MetaMessageScrollableDto;
import de.gematik.test.tiger.proxy.data.RbelTreeResponseScrollableDto;
import de.gematik.test.tiger.proxy.data.ResetMessagesDto;
import de.gematik.test.tiger.proxy.data.SearchMessagesScrollableDto;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Data
@RequiredArgsConstructor
@RestController
@RequestMapping({"nextwebui"})
@Validated
@Slf4j
public class TigerScrollableWebUiController implements ApplicationContextAware {
  private static final int MAX_MESSAGES_PER_FILTER_REQUEST = 100;
  private static final int MAX_MESSAGES_PER_SEARCH_REQUEST = 10;
  private static final String RBEL_KEY_CONTENT = ".content";

  /**
   * in error responses on http requests, this token causes problems so remove it for better
   * handling on client side
   */
  public static final String REGEX_STATUSCODE_TOKEN = ".*:\\d* ";

  private TigerProxy tigerProxy;
  private final RbelHtmlRenderer renderer;

  private final TigerProxyConfiguration proxyConfiguration;
  private ApplicationContext applicationContext;

  public final SimpMessagingTemplate template;
  private final TigerBuildPropertiesService buildProperties;

  private final TigerWebUiController webUiController;

  @Override
  public void setApplicationContext(final ApplicationContext appContext) throws BeansException {
    this.applicationContext = appContext;
  }

  @GetMapping(value = "/testJexlQuery", produces = MediaType.APPLICATION_JSON_VALUE)
  public JexlQueryResponseScrollableDto testJexlQuery(
      @RequestParam(name = "messageUuid") final String messageUuid,
      @RequestParam(name = "query") final String query) {
    var response = JexlQueryResponseScrollableDto.builder().messageUuid(messageUuid).query(query);

    final var targetMessage =
        getTigerProxy().getRbelLogger().getMessageHistory().stream()
            .filter(msg -> msg.getUuid().equals(messageUuid))
            .findFirst()
            .orElseThrow();

    final var messageContext =
        TigerJexlExecutor.buildJexlMapContext(targetMessage, Optional.empty());

    response = response.messageContext(messageContext);

    try {
      return response
          .matchSuccessful(TigerJexlExecutor.matchesAsJexlExpression(targetMessage, query))
          .build();
    } catch (JexlException | TigerJexlException jexlException) {
      log.warn("Failed to perform JEXL query '" + query + "'", jexlException);
      String msg = jexlException.getMessage();
      msg = msg.replaceAll(REGEX_STATUSCODE_TOKEN, "");
      return response.errorMessage(msg).build();

    } catch (RuntimeException rte) {
      log.warn("Runtime failure while performing JEXL query '" + query + "'", rte);
      String msg = rte.getMessage();
      msg = msg.replaceAll(REGEX_STATUSCODE_TOKEN, "");
      return response.errorMessage(msg).build();
    }
  }

  @GetMapping(value = "/testRbelExpression", produces = MediaType.APPLICATION_JSON_VALUE)
  public RbelTreeResponseScrollableDto testRbelExpression(
      @RequestParam(name = "messageUuid") final String msgUuid,
      @RequestParam(name = "query") final String query) {
    final var response = RbelTreeResponseScrollableDto.builder().messageUuid(msgUuid).query(query);

    List<RbelElement> targetElements;
    try {
      targetElements =
          getTigerProxy().getRbelLogger().getMessageHistory().stream()
              .filter(msg -> msg.getUuid().equals(msgUuid))
              .map(msg -> msg.findRbelPathMembers(query))
              .flatMap(List::stream)
              .toList();
      if (targetElements.isEmpty()) {
        return response.build();
      }
    } catch (RbelPathException rbelPathException) {
      log.warn("Failed to perform RBelPath query '{}'", query, rbelPathException);
      String msg = rbelPathException.getMessage();
      msg = msg.replaceAll(REGEX_STATUSCODE_TOKEN, "");
      return response.errorMessage(msg).build();
    }
    try {
      return response
          .elementsWithTree(
              targetElements.stream()
                  .map(
                      rbelElement -> {
                        final var html = createRbelTreeForElement(rbelElement, true, query);
                        final var key = rbelElement.findNodePath();
                        final var el =
                            key.endsWith(RBEL_KEY_CONTENT) && !query.endsWith(RBEL_KEY_CONTENT)
                                ? "$." + key.substring(0, key.length() - RBEL_KEY_CONTENT.length())
                                : "$." + key;
                        return new AbstractMap.SimpleEntry<>(el, html);
                      })
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .build();
    } catch (JexlException | TigerJexlException jexlException) {
      log.warn("Failed to perform RBelPath query '{}'", query, jexlException);
      String msg = jexlException.getMessage();
      msg = msg.replaceAll(REGEX_STATUSCODE_TOKEN, "");
      return response.errorMessage(msg).build();

    } catch (RuntimeException rte) {
      log.warn("Runtime failure while performing RbelPath query '{}'", query, rte);
      String msg = rte.getMessage();
      msg = msg.replaceAll(REGEX_STATUSCODE_TOKEN, "");
      return response.errorMessage(msg).build();
    }
  }

  private String createRbelTreeForElement(
      RbelElement targetElement, boolean addJexlResponseLinkCssClass, String rbelPath) {

    RbelElement rootElement =
        targetElement
            .getKey()
            .filter(key -> key.endsWith("content") && !rbelPath.endsWith("content"))
            .map(key -> targetElement.getParentNode())
            .orElse(targetElement);

    return HtmlEscapers.htmlEscaper()
        .escape(
            RbelElementTreePrinter.builder()
                .rootElement(rootElement)
                .printFacets(false)
                .build()
                .execute())
        .replace(RbelAnsiColors.RESET.toString(), "</span>")
        .replace(
            RbelAnsiColors.RED_BOLD.toString(),
            "<span class='text-warning "
                + (addJexlResponseLinkCssClass ? "jexlResponseLink' style='cursor: pointer;'" : "'")
                + ">")
        .replace(RbelAnsiColors.CYAN.toString(), "<span class='text-info'>")
        .replace(
            RbelAnsiColors.YELLOW_BRIGHT.toString(),
            "<span class='text-danger has-text-weight-bold'>")
        .replace(RbelAnsiColors.GREEN.toString(), "<span class='text-warning'>")
        .replace(RbelAnsiColors.BLUE.toString(), "<span class='text-success'>")
        .replace("\n", "<br/>");
  }

  /** Returns a stable hash, even if the message queue is empty. */
  private String messageHash() {
    var messages = getTigerProxy().getRbelMessages();
    return messages.isEmpty()
        ? (new UUID(getTigerProxy().hashCode(), 1234)).toString()
        : messages.getFirst().getUuid();
  }

  private <T> void addOffsetToMessages(
      long startOffset, List<T> messages, ObjLongConsumer<T> offsetSetter) {
    for (int i = 0; i < messages.size(); i++) {
      offsetSetter.accept(messages.get(i), i + startOffset); // Apply the setter with the index
    }
  }

  @GetMapping(value = "/getMessagesWithHtml", produces = MediaType.APPLICATION_JSON_VALUE)
  public GetMessagesWithHtmlScrollableDto getMessagesWithHtml(
      @RequestParam(name = "fromOffset") int fromOffset,
      @RequestParam(name = "toOffsetExcluding") int toOffsetExcluding,
      @RequestParam(name = "filterRbelPath", required = false) String filterRbelPath) {

    if (toOffsetExcluding < fromOffset)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "`toOffsetExcluding` must be greater or equal than `fromOffset`");

    final var messages = new LinkedList<>(getTigerProxy().getRbelLogger().getMessageHistory());
    var total = messages.size();

    var result = new GetMessagesWithHtmlScrollableDto();
    result.setFromOffset(fromOffset);
    result.setToOffsetExcluding(toOffsetExcluding);
    result.setFilter(GetMessagesFilterScrollableDto.builder().rbelPath(filterRbelPath).build());

    result.setTotal(total);

    result.setHash(messageHash());

    var messageStream = messages.stream();
    messageStream = filterMessages(messageStream, filterRbelPath);

    result.setMessages(
        messageStream
            .skip(fromOffset)
            .limit((long) toOffsetExcluding - fromOffset)
            .map(
                msg ->
                    HtmlMessageScrollableDto.builder()
                        .content(
                            new RbelHtmlRenderingToolkit(renderer).convertMessage(msg).render())
                        .uuid(msg.getUuid())
                        .sequenceNumber(MessageMetaDataDto.getElementSequenceNumber(msg))
                        .build())
            .toList());

    addOffsetToMessages(fromOffset, result.getMessages(), HtmlMessageScrollableDto::setOffset);

    result.setTotalFiltered(result.getMessages().size());

    return result;
  }

  @GetMapping(value = "/getMessagesWithMeta", produces = MediaType.APPLICATION_JSON_VALUE)
  public GetMessagesWithMetaScrollableDto getMessagesWithMeta(
      @RequestParam(name = "filterRbelPath", required = false) String filterRbelPath) {
    final var messages = new LinkedList<>(getTigerProxy().getRbelLogger().getMessageHistory());
    var total = messages.size();

    var result = new GetMessagesWithMetaScrollableDto();

    result.setTotal(total);
    result.setHash(messageHash());
    result.setFilter(GetMessagesFilterScrollableDto.builder().rbelPath(filterRbelPath).build());

    var messageStream = messages.stream();
    messageStream = filterMessages(messageStream, filterRbelPath);

    result.setMessages(messageStream.map(MetaMessageScrollableDto::createFrom).toList());

    addOffsetToMessages(0, result.getMessages(), MetaMessageScrollableDto::setOffset);

    result.setTotalFiltered(result.getMessages().size());

    return result;
  }

  @GetMapping(value = "/testFilterMessages", produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchMessagesScrollableDto testFilterMessages(
      @RequestParam(name = "filterRbelPath", required = false) String filterRbelPath) {
    final var messages = new LinkedList<>(getTigerProxy().getRbelLogger().getMessageHistory());
    var total = messages.size();

    var result = new SearchMessagesScrollableDto();

    result.setTotal(total);
    result.setHash(messageHash());
    result.setFilter(GetMessagesFilterScrollableDto.builder().rbelPath(filterRbelPath).build());

    var messageStream = messages.stream();
    try {
      // Retrieve one more message to check for additional data; trim the extra later.
      messageStream =
          filterMessages(messageStream, filterRbelPath).limit(MAX_MESSAGES_PER_FILTER_REQUEST + 1);

      final var totalFilteredMessages = messageStream.count();
      if (totalFilteredMessages > MAX_MESSAGES_PER_FILTER_REQUEST) {
        result.setTotalFiltered(MAX_MESSAGES_PER_FILTER_REQUEST + "+");
      } else {
        result.setTotalFiltered(String.valueOf(totalFilteredMessages));
      }
    } catch (JexlException | TigerJexlException e) {
      log.info(e.getMessage(), e);
      result.setErrorMessage(e.getMessage());
    }

    return result;
  }

  @GetMapping(value = "/searchMessages", produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchMessagesScrollableDto searchMessages(
      @RequestParam(name = "filterRbelPath") String filterRbelPath,
      @RequestParam(name = "searchRbelPath") String searchRbelPath) {

    var rbelLogger = tigerProxy.getRbelLogger();
    var total = rbelLogger.getMessageHistory().size();

    var messages = getTigerProxy().getRbelLogger().getMessageHistory();

    var result = new SearchMessagesScrollableDto();

    result.setTotal(total);
    result.setHash(messageHash());
    result.setFilter(GetMessagesFilterScrollableDto.builder().rbelPath(filterRbelPath).build());
    result.setSearchFilter(
        GetMessagesFilterScrollableDto.builder().rbelPath(searchRbelPath).build());

    var messageStream = messages.stream();
    try {
      messageStream = filterMessages(messageStream, filterRbelPath);
      messageStream = filterMessages(messageStream, searchRbelPath);

      result.setMessages(
          messageStream
              .limit(MAX_MESSAGES_PER_SEARCH_REQUEST + 1)
              .map(MetaMessageScrollableDto::createFrom)
              .toList());

      final var totalFilteredMessages = result.getMessages().size();
      if (totalFilteredMessages > MAX_MESSAGES_PER_SEARCH_REQUEST) {
        result.setMessages(result.getMessages().subList(0, MAX_MESSAGES_PER_SEARCH_REQUEST));
        result.setTotalFiltered(MAX_MESSAGES_PER_SEARCH_REQUEST + "+");
      } else {
        result.setTotalFiltered(String.valueOf(totalFilteredMessages));
      }
    } catch (JexlException | TigerJexlException e) {
      log.info(e.getMessage(), e);
      result.setErrorMessage(e.getMessage());
    }

    return result;
  }

  private Stream<RbelElement> filterMessages(Stream<RbelElement> stream, String filterRbelPath) {
    var actualFilterRbelPath =
        filterRbelPath != null && filterRbelPath.isBlank() ? null : filterRbelPath;

    return actualFilterRbelPath != null
        ? stream.filter(
            msg ->
                TigerJexlExecutor.matchesAsJexlExpression(
                        msg, actualFilterRbelPath, Optional.empty())
                    || TigerJexlExecutor.matchesAsJexlExpression(
                        findPartner(msg), actualFilterRbelPath, Optional.empty()))
        : stream;
  }

  private RbelElement findPartner(RbelElement msg) {
    return msg.getFacet(TracingMessagePairFacet.class)
        .map(
            pairFacet -> {
              if (pairFacet.getRequest() == msg) {
                return pairFacet.getResponse();
              } else {
                return pairFacet.getRequest();
              }
            })
        .orElse(null);
  }

  @GetMapping(value = "/resetMessages", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResetMessagesDto resetMessages() {
    return webUiController.resetMessages();
  }

  @GetMapping(value = "/quit", produces = MediaType.APPLICATION_JSON_VALUE)
  public void quitProxy(
      @RequestParam(name = "noSystemExit", required = false) final String noSystemExit) {
    webUiController.quitProxy(noSystemExit);
  }

  @PostMapping(value = "/importTraffic")
  public void importTraffic(@RequestBody String rawTraffic) {
    webUiController.importTrafficFromFile(rawTraffic);
  }
}
