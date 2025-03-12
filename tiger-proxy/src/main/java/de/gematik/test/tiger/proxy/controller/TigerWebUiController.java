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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.data.util.RbelElementTreePrinter;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.rbellogger.renderer.MessageMetaDataDto;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelJexlExecutor;
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
import jakarta.servlet.http.HttpServletResponse;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Data
@RequiredArgsConstructor
@RestController
@RequestMapping({"webui", "/"})
@Validated
@Slf4j
public class TigerWebUiController implements ApplicationContextAware {
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

  @Override
  public void setApplicationContext(final ApplicationContext appContext) throws BeansException {
    this.applicationContext = appContext;
  }

  @GetMapping(value = "")
  public ResponseEntity<Resource> getIndex() {
    final var resource = new ClassPathResource("/static/webui/index.html");
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html")).body(resource);
  }

  @GetMapping(value = "/assets/{asset}")
  public ResponseEntity<Resource> getAsset(@PathVariable("asset") String assetFile) {
    final var resource = new ClassPathResource("/static/webui/assets/" + assetFile);
    if (!resource.exists()) {
      return ResponseEntity.notFound().build();
    }
    final var fileExt = StringUtils.getFilenameExtension(assetFile);
    if (fileExt == null) {
      return ResponseEntity.notFound().build();
    }
    String contentType =
        switch (fileExt) {
          case "css" -> "text/css";
          case "js" -> "application/javascript";
          case "html", "htm" -> "text/html";
          case "png" -> "image/png";
          case "jpg", "jpeg" -> "image/jpeg";
          case "gif" -> "image/gif";
          case "svg" -> "image/svg+xml";
          case "woff" -> "font/woff";
          case "woff2" -> "font/woff2";
          case "ttf" -> "font/ttf";
          default -> "application/octet-stream";
        };
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
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
                        final var html = createRbelTreeForElement(rbelElement, query);
                        final var key = rbelElement.findNodePath();
                        final var el =
                            key.endsWith(RBEL_KEY_CONTENT) && !query.endsWith(RBEL_KEY_CONTENT)
                                ? "$." + key.substring(0, key.length() - RBEL_KEY_CONTENT.length())
                                : "$." + key;
                        return new AbstractMap.SimpleEntry<>(el, html);
                      })
                  .collect(Collectors.toList()))
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

  private String createRbelTreeForElement(RbelElement targetElement, String rbelPath) {

    RbelElement rootElement =
        targetElement
            .getKey()
            .filter(key -> key.endsWith("content") && !rbelPath.endsWith("content"))
            .map(key -> targetElement.getParentNode())
            .orElse(targetElement);

    return RbelElementTreePrinter.builder()
        .rootElement(rootElement)
        .printFacets(true)
        .htmlEscaping(true)
        .addJexlResponseLinkCssClass(true)
        .build()
        .execute();
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

  @GetMapping(value = "/trafficLog*.tgr", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public String downloadTraffic(
      @RequestParam(name = "lastMsgUuid", required = false) final String lastMsgUuid,
      @RequestParam(name = "filterRbelPath", required = false) final String filterCriterion,
      @RequestParam(name = "pageSize", required = false) final Optional<Integer> pageSize,
      HttpServletResponse response) {
    int actualPageSize =
        pageSize.orElse(getProxyConfiguration().getMaximumTrafficDownloadPageSize());
    final List<RbelElement> filteredMessages =
        loadMessagesMatchingFilter(lastMsgUuid, filterCriterion);
    final int returnedMessages = Math.min(filteredMessages.size(), actualPageSize);
    response.addHeader("available-messages", String.valueOf(filteredMessages.size()));
    response.addHeader("returned-messages", String.valueOf(returnedMessages));

    final String result =
        filteredMessages.stream()
            .limit(actualPageSize)
            .map(tigerProxy.getRbelFileWriter()::convertToRbelFileString)
            .collect(Collectors.joining("\n\n"));

    if (!result.isEmpty()) {
      response.addHeader("last-uuid", filteredMessages.get(returnedMessages - 1).getUuid());
    }
    return result;
  }

  private List<RbelElement> loadMessagesMatchingFilter(String lastMsgUuid, String filterCriterion) {
    return getTigerProxy().getRbelLogger().getMessageHistory().stream()
        .filter(
            msg -> {
              if (!StringUtils.hasText(filterCriterion)) {
                return true;
              }
              if (filterCriterion.startsWith("\"") && filterCriterion.endsWith("\"")) {
                final String textFilter =
                    filterCriterion.substring(1, filterCriterion.length() - 1);
                return RbelJexlExecutor.matchAsTextExpression(msg, textFilter)
                    || RbelJexlExecutor.matchAsTextExpression(findPartner(msg), textFilter);
              } else {
                return TigerJexlExecutor.matchesAsJexlExpression(
                        msg, filterCriterion, Optional.empty())
                    || TigerJexlExecutor.matchesAsJexlExpression(
                        findPartner(msg), filterCriterion, Optional.empty());
              }
            })
        .dropWhile(messageIsBefore(lastMsgUuid))
        .filter(msg -> !msg.getUuid().equals(lastMsgUuid))
        .toList();
  }

  private static Predicate<RbelElement> messageIsBefore(String lastMsgUuid) {
    return msg -> {
      if (StringUtils.hasText(lastMsgUuid)) {
        return !msg.getUuid().equals(lastMsgUuid);
      } else {
        return false;
      }
    };
  }

  @GetMapping(value = "/resetMessages", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResetMessagesDto resetMessages() {
    log.info("Resetting currently recorded messages on rbel logger..");
    int size = getTigerProxy().getRbelLogger().getMessageHistory().size();
    ResetMessagesDto result = new ResetMessagesDto();
    result.setNumMsgs(size);
    getTigerProxy().getRbelLogger().clearAllMessages();
    return result;
  }

  @GetMapping(value = "/quit", produces = MediaType.APPLICATION_JSON_VALUE)
  public void quitProxy(
      @RequestParam(name = "noSystemExit", required = false) final String noSystemExit) {
    log.info("Shutting down tiger standalone proxy at port " + tigerProxy.getProxyPort() + "...");
    tigerProxy.close();
    log.info("Shutting down tiger standalone proxy ui...");
    int exitCode = SpringApplication.exit(applicationContext);
    if (exitCode != 0) {
      log.warn("Exit of tiger proxy ui not successful - exit code: " + exitCode);
    }
    if (!StringUtils.hasText(noSystemExit)) {
      System.exit(0);
    }
  }

  @PostMapping(value = "/importTraffic")
  public void importTraffic(@RequestBody String rawTraffic) {
    tigerProxy.readTrafficFromString(rawTraffic);
  }
}
