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
 *
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.RbelLoggerWriter;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.enums.ModeType;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.json.JsonSchemaChecker;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.xml.transform.Source;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.collections4.iterators.ReverseListIterator;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

@SuppressWarnings("unused")
@Slf4j
public class RbelMessageValidator {

  public static final String FOUND_IN_MESSAGES = "' found in messages";

  private static final List<String> EMPTY_PATH = List.of("", "/");
  public static final TigerTypedConfigurationKey<Integer> RBEL_REQUEST_TIMEOUT =
      new TigerTypedConfigurationKey<>("tiger.rbel.request.timeout", Integer.class);

  private static final Map<String, UnaryOperator<DiffBuilder>> DIFF_OPTIONS = new HashMap<>();

  static {
    DIFF_OPTIONS.put("nocomment", DiffBuilder::ignoreComments);
    DIFF_OPTIONS.put("txtignoreempty", DiffBuilder::ignoreElementContentWhitespace);
    DIFF_OPTIONS.put("txttrim", DiffBuilder::ignoreWhitespace);
    DIFF_OPTIONS.put("txtnormalize", DiffBuilder::normalizeWhitespace);
  }

  private final TigerTestEnvMgr tigerTestEnvMgr;
  private final TigerProxy tigerProxy;

  @Setter @Getter protected RbelElement currentRequest;

  // contains either currentRequest or currentResponse
  private RbelElement lastFoundMessage;

  @Setter(AccessLevel.PROTECTED)
  @Getter
  protected RbelElement currentResponse;

  public RbelMessageValidator() {
    this(
        TigerDirector.getTigerTestEnvMgr(),
        TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail());
  }

  @VisibleForTesting
  public RbelMessageValidator(TigerTestEnvMgr tigerTestEnvMgr, TigerProxy tigerProxy) {
    TigerJexlExecutor.registerAdditionalNamespace("rbel", new JexlToolbox());
    this.tigerTestEnvMgr = tigerTestEnvMgr;
    this.tigerProxy = tigerProxy;
  }

  public List<RbelElement> getRbelMessages() {
    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    return new UnmodifiableList<>(
        new ArrayList<>(LocalProxyRbelMessageListener.getInstance().getValidatableRbelMessages()));
  }

  public void clearRbelMessages() {
    LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
  }

  @SuppressWarnings("java:S1135")
  public void filterRequestsAndStoreInContext(final RequestParameter requestParameter) {
    RbelElement message = tryFindMessageByDescription(requestParameter);
    if (message.hasFacet(RbelRequestFacet.class)) {
      storeRequestAndWaitForAndStoreResponse(message);
    } else if (message.hasFacet(RbelResponseFacet.class)) {
      storeResponseAndSearchAndStoreRequest(message);
    } else {
      log.atInfo()
          // TODO TGR-1528 use element short description mechanism instead of getRawStringContent
          .addArgument(message::getRawStringContent)
          .log("Found message that is neither request nor response:\n\n{}");
    }
  }

  private RbelElement tryFindMessageByDescription(RequestParameter requestParameter) {
    try {
      lastFoundMessage = findMessageByDescription(requestParameter);
      return lastFoundMessage;
    } catch (AssertionError e) {
      clearCurrentMessages();
      throw e;
    }
  }

  @VisibleForTesting
  public void clearCurrentMessages() {
    setCurrentRequest(null);
    setCurrentResponse(null);
    lastFoundMessage = null;
  }

  @SuppressWarnings("java:S1135")
  private void storeRequestAndWaitForAndStoreResponse(RbelElement request) {
    setCurrentRequest(request);
    setCurrentResponse(null);
    if (request
        .getFacet(RbelRequestFacet.class)
        .filter(RbelRequestFacet::isResponseRequired)
        .isPresent()) {
      try {
        final int requestTimeout = RBEL_REQUEST_TIMEOUT.getValue().orElse(5);
        await("Waiting for matching request")
            .atMost(requestTimeout, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(
                () ->
                    tigerTestEnvMgr.isShouldAbortTestExecution()
                        || findAndStoreCorrespondingResponse(request));
        if (tigerTestEnvMgr.isShouldAbortTestExecution()) {
          throw new AssertionError("User aborted test run");
        }
      } catch (final ConditionTimeoutException cte) {
        log.atError()
            // TODO TGR-1528 use element short description mechanism instead of getRawStringContent
            .addArgument(request::getRawStringContent)
            .log("Missing response to filtered request!\n\n{}");
        throw new TigerLibraryException("Missing response to filtered request!", cte);
      }
    }
  }

  @SuppressWarnings("java:S1135")
  private void storeResponseAndSearchAndStoreRequest(RbelElement response) {
    setCurrentResponse(response);
    if (!findAndStoreCorrespondingRequest(response)) {
      setCurrentRequest(response);
      log.atInfo()
          // TODO TGR-1528 use element short description mechanism instead of getRawStringContent
          .addArgument(response::getRawStringContent)
          .log("Missing request to filtered response!\n\n{}");
    }
  }

  private boolean findAndStoreCorrespondingResponse(RbelElement message) {
    return findAndStoreCorrespondingOtherMessage(
        message,
        RbelResponseFacet.class,
        TracingMessagePairFacet::getResponse,
        this::setCurrentResponse);
  }

  private boolean findAndStoreCorrespondingRequest(RbelElement message) {
    return findAndStoreCorrespondingOtherMessage(
        message,
        RbelRequestFacet.class,
        TracingMessagePairFacet::getRequest,
        this::setCurrentRequest);
  }

  private boolean findAndStoreCorrespondingOtherMessage(
      RbelElement message,
      Class<? extends RbelFacet> otherMessageFacet,
      Function<TracingMessagePairFacet, RbelElement> getOtherMessageFromPair,
      Consumer<RbelElement> storeOtherMessage) {
    return message.getFacet(TracingMessagePairFacet.class).stream()
        .map(getOtherMessageFromPair)
        .filter(msg -> msg.hasFacet(otherMessageFacet))
        .anyMatch(
            msg -> {
              storeOtherMessage.accept(msg);
              return true;
            });
  }

  public RbelElement waitForMessageToBePresent(final RequestParameter requestParameter) {
    return findMessageByDescription(requestParameter);
  }

  protected RbelElement findMessageByDescription(final RequestParameter requestParameter) {
    final int waitsec = RBEL_REQUEST_TIMEOUT.getValue().orElse(5);

    Optional<RbelElement> initialElement = getInitialElement(requestParameter);

    final AtomicReference<RbelElement> candidate = new AtomicReference<>();
    try {
      await("Waiting for matching request")
          .atMost(waitsec, TimeUnit.SECONDS)
          .pollDelay(0, TimeUnit.SECONDS)
          .pollInterval(400, TimeUnit.MILLISECONDS)
          .until(
              () -> {
                if (tigerTestEnvMgr.isShouldAbortTestExecution()) {
                  return true;
                }
                final Optional<RbelElement> found =
                    filterRequests(requestParameter, initialElement);
                found.ifPresent(candidate::set);
                return found.isPresent();
              });
      if (tigerTestEnvMgr.isShouldAbortTestExecution()) {
        throw new AssertionError("User aborted test run");
      }
    } catch (final ConditionTimeoutException cte) {
      log.error("Didn't find any matching messages!");
      printAllPathsOfMessages(getRbelMessages());
      if (requestParameter.getPath() == null) {
        throw new AssertionError(String.format(
            "No request with matching rbelPath '%s%s",
            requestParameter.getRbelPath(), FOUND_IN_MESSAGES));
      } else if (requestParameter.getRbelPath() == null) {
        throw new AssertionError(String.format(
            "No request with path '%s%s", requestParameter.getPath(), FOUND_IN_MESSAGES));
      } else {
        throw new AssertionError(String.format(
            "No request with path '%s' and rbelPath '%s' matching '%s%s",
            requestParameter.getPath(),
            requestParameter.getRbelPath(),
            StringUtils.abbreviate(requestParameter.getValue(), 300),
            FOUND_IN_MESSAGES));
      }
    }
    return candidate.get();
  }

  private Optional<RbelElement> getInitialElement(RequestParameter requestParameter) {
    var validatableRbelMessages =
        LocalProxyRbelMessageListener.getInstance().getValidatableRbelMessages();
    if (requestParameter.isStartFromLastMessage()) {
      var markerMessage =
          requestParameter.isRequireRequestMessage() ? currentRequest : lastFoundMessage;
      return validatableRbelMessages.stream()
          .dropWhile(msg -> msg != markerMessage)
          .skip(1)
          .findFirst();
    } else if (requestParameter.isRequireNewMessage() && !validatableRbelMessages.isEmpty()) {
      return Optional.ofNullable(validatableRbelMessages.getLast());
    } else {
      return Optional.empty();
    }
  }

  protected Optional<RbelElement> filterRequests(
      final RequestParameter requestParameter, Optional<RbelElement> startFromMessageInclusively) {
    List<RbelElement> msgs =
        getRbelElementsOptionallyFromGivenMessageInclusively(startFromMessageInclusively);
    final String hostFilter = TigerConfigurationKeys.REQUEST_FILTER_HOST.getValueOrDefault();
    final String methodFilter = TigerConfigurationKeys.REQUEST_FILTER_METHOD.getValueOrDefault();

    List<RbelElement> candidateMessages =
        getCandidateMessages(requestParameter, msgs, hostFilter, methodFilter);
    if (candidateMessages.isEmpty()) {
      return Optional.empty();
    }

    if (StringUtils.isEmpty(requestParameter.getRbelPath())) {
      if (candidateMessages.size() > 1) {
        log.atWarn()
            .addArgument(() -> requestParameter.isFilterPreviousRequest() ? "last" : "first")
            .log(
                "Found more then one candidate message. Returning {} message. This may not be"
                    + " deterministic!");
        printAllPathsOfMessages(candidateMessages);
      }
      if (requestParameter.isFilterPreviousRequest()) {
        return Optional.of(candidateMessages.get(candidateMessages.size() - 1));
      } else {
        return Optional.of(candidateMessages.get(0));
      }
    }

    if (requestParameter.isFilterPreviousRequest()) {
      candidateMessages = Lists.reverse(candidateMessages);
    }

    return filterMatchingCandidateMessages(requestParameter, candidateMessages);
  }

  private List<RbelElement> getRbelElementsOptionallyFromGivenMessageInclusively(
      Optional<RbelElement> startFromMessageExclusively) {
    List<RbelElement> msgs = getRbelMessages();
    if (startFromMessageExclusively.isPresent()) {
      int idx = -1;
      for (var i = 0; i < msgs.size(); i++) {
        if (msgs.get(i) == startFromMessageExclusively.get()) {
          idx = i;
          break;
        }
      }
      if (idx > 0) {
        msgs = new ArrayList<>(msgs.subList(idx, msgs.size()));
      }
    }
    return msgs;
  }

  @NotNull
  private List<RbelElement> getCandidateMessages(
      RequestParameter requestParameter,
      List<RbelElement> msgs,
      String hostFilter,
      String methodFilter) {
    return msgs.stream()
        .filter(
            el ->
                !requestParameter.isRequireRequestMessage() || el.hasFacet(RbelRequestFacet.class))
        .filter(req -> doesPathOfMessageMatch(req, requestParameter.getPath()))
        .filter(req -> hostFilter == null || hostFilter.isEmpty() || doesHostMatch(req, hostFilter))
        .filter(
            req ->
                methodFilter == null
                    || methodFilter.isEmpty()
                    || doesMethodMatch(req, methodFilter))
        .toList();
  }

  @NotNull
  private Optional<RbelElement> filterMatchingCandidateMessages(
      RequestParameter requestParameter, List<RbelElement> candidateMessages) {
    for (final RbelElement candidateMessage : candidateMessages) {
      final List<RbelElement> pathExecutionResult =
          new RbelPathExecutor<>(candidateMessage, requestParameter.getRbelPath()).execute();
      if (pathExecutionResult.isEmpty()) {
        continue;
      }
      if (StringUtils.isEmpty(requestParameter.getValue())) {
        return Optional.of(candidateMessage);
      } else {
        final String content =
            pathExecutionResult.stream()
                .map(RbelMessageValidator::getValueOrContentString)
                .map(String::trim)
                .collect(Collectors.joining());
        try {
          if (content.equals(requestParameter.getValue())
              || content.matches(requestParameter.getValue())
              || Pattern.compile(requestParameter.getValue(), Pattern.DOTALL)
                  .matcher(content)
                  .matches()) {
            return Optional.of(candidateMessage);
          } else {
            log.atInfo()
                .addArgument(() -> StringUtils.abbreviate(content, 300))
                .addArgument(() -> StringUtils.abbreviate(requestParameter.getValue(), 300))
                .log("Found rbel node but \n'{}' didnt match\n'{}'");
          }
        } catch (final Exception ex) {
          log.error(
              "Failure while trying to apply regular expression '{}'!",
              requestParameter.getValue(),
              ex);
        }
      }
    }
    return Optional.empty();
  }

  public boolean doesPathOfMessageMatch(final RbelElement req, final String path) {
    if (path == null) {
      return true;
    }
    try {
      final URI uri =
          new URI(
              req.getFacet(RbelHttpRequestFacet.class)
                  .map(RbelHttpRequestFacet::getPath)
                  .map(RbelMessageValidator::getValueOrContentString)
                  .orElse(""));
      boolean match = doesItMatch(uri.getPath(), path);
      if (!match && EMPTY_PATH.contains(path) && EMPTY_PATH.contains(uri.getPath())) {
        match = true;
      }
      return match;
    } catch (final URISyntaxException e) {
      return false;
    }
  }

  public boolean doesHostMatch(final RbelElement req, final String hostFilter) {
    val host =
        req.getFacet(RbelTcpIpMessageFacet.class)
            .flatMap(e -> RbelHostnameFacet.tryToExtractServerName(e.getReceiver()))
            .orElse("");

    return doesItMatch(host, hostFilter);
  }

  public boolean doesMethodMatch(final RbelElement req, final String method) {
    final String reqMethod =
        req.getFacet(RbelHttpRequestFacet.class)
            .map(RbelHttpRequestFacet::getMethod)
            .map(RbelElement::getRawStringContent)
            .map(String::toUpperCase)
            .orElse("");
    return doesItMatch(reqMethod, method);
  }

  private boolean doesItMatch(final String toTest, String matchingString) {
    try {
      return StringUtils.equals(toTest, matchingString) || toTest.matches(matchingString);
    } catch (PatternSyntaxException rte) {
      log.error("Probable error while parsing regex!", rte);
      return false;
    }
  }

  public void assertAttributeOfCurrentResponseMatches(
      final String rbelPath, final String value, boolean shouldMatch) {
    RbelMessageNodeElementMatchExecutor.builder()
        .rbelPath(rbelPath)
        .shouldMatch(shouldMatch)
        .oracle(value)
        .elements(findElementsInCurrentResponse(rbelPath))
        .build()
        .execute();
  }

  public void assertAttributeOfCurrentRequestMatches(
      final String rbelPath, final String value, boolean shouldMatch) {
    RbelMessageNodeElementMatchExecutor.builder()
            .rbelPath(rbelPath)
            .shouldMatch(shouldMatch)
            .oracle(value)
            .elements(findElementsInCurrentRequest(rbelPath))
            .build()
            .execute();
  }

  public void assertAttributeOfCurrentResponseMatchesAs(
      String rbelPath, ModeType mode, String oracle, String diffOptionCsv) {
    assertAttributeForMessagesMatchAs(
        mode, oracle, findElementsInCurrentResponse(rbelPath), diffOptionCsv);
  }

  public void assertAttributeOfCurrentRequestMatchesAs(
      String rbelPath, ModeType mode, String oracle) {
    assertAttributeForMessagesMatchAs(mode, oracle, findElementsInCurrentRequest(rbelPath), "");
  }

  public void assertAttributeForMessagesMatchAs(
      ModeType mode, String oracle, List<RbelElement> elements, String diffOptionCSV) {
    for (RbelElement element : elements) {
      try {
        switch (mode) {
          case JSON ->
              new JsonChecker().compareJsonStrings(getAsJsonString(element), oracle, false);
          case XML -> compareXMLStructureOfRbelElement(element, oracle, diffOptionCSV);
          case JSON_SCHEMA ->
              new JsonSchemaChecker().compareJsonToSchema(getAsJsonString(element), oracle);
        }
        log.debug("Found matching element: \n{}", element.printTreeStructure());
        return;
      } catch (JsonChecker.JsonCheckerMismatchException | AssertionError ignored) {
        // try next
      }
    }
    if (elements.size() == 1) {
      throw new AssertionError(
          String.format(
              """
                      Element value:
                      %s
                      Expected:
                      %s""",
              elements.get(0).getRawStringContent(), oracle));
    } else {
      throw new AssertionError(
          String.format(
              "No matching element for value %s found in list of %d elements! ",
              oracle, elements.size()));
    }
  }

  private String getAsJsonString(RbelElement target) {
    if (target.hasFacet(RbelJsonFacet.class)) {
      return target.getRawStringContent();
    } else if (target.hasFacet(RbelCborFacet.class)) {
      return target
          .getFacet(RbelCborFacet.class)
          .map(RbelCborFacet::getNode)
          .map(JsonNode::toString)
          .orElse("");
    } else {
      throw new AssertionError("Node is neither JSON nor CBOR, can not match with JSON");
    }
  }

  private void printAllPathsOfMessages(final List<RbelElement> msgs) {
    long requests =
        msgs.stream().filter(msg -> msg.getFacet(RbelHttpRequestFacet.class).isPresent()).count();
    log.info(
        "Found the following {} messages:\n{} ",
        requests,
        msgs.stream()
            .map(msg -> msg.getFacet(RbelHttpRequestFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(req -> "=>\t" + req.getPathAsString() + " : " + req.getChildElements())
            .collect(Collectors.joining("\n")));
  }

  public void compareXMLStructure(
      final String test, final String oracle, final List<UnaryOperator<DiffBuilder>> diffOptions) {
    final ArrayList<Difference> diffs = new ArrayList<>();
    final Source srcTest = Input.from(test).build();
    final Source srcOracle = Input.from(oracle).build();
    DiffBuilder db = DiffBuilder.compare(srcOracle).withTest(srcTest);
    for (final UnaryOperator<DiffBuilder> src : diffOptions) {
      db = src.apply(db);
    }

    db = db.checkForSimilar();
    db.withDifferenceEvaluator(
        (comparison, outcome) -> {
          if (outcome != ComparisonResult.EQUAL
              && (comparison.getType() == ComparisonType.NAMESPACE_URI
                  || comparison.getType() == ComparisonType.NAMESPACE_PREFIX)) {
            return ComparisonResult.SIMILAR;
          }
          return outcome;
        });

    final Diff diff = db.build();
    assertThat(diff.hasDifferences()).withFailMessage("XML tree mismatch!\n" + diff).isFalse();
  }

  public void compareXMLStructure(final String test, final String oracle) {
    compareXMLStructure(test, oracle, Collections.emptyList());
  }

  @SneakyThrows
  public void compareXMLStructure(
      final String test, final String oracle, final String diffOptionCSV) {
    final List<UnaryOperator<DiffBuilder>> diffOptions = new ArrayList<>();
    Arrays.stream(diffOptionCSV.split(","))
        .map(String::trim)
        .forEach(
            srcClassId -> {
              assertThat(DIFF_OPTIONS).containsKey(srcClassId);
              diffOptions.add(DIFF_OPTIONS.get(srcClassId));
            });
    compareXMLStructure(test, oracle, diffOptions);
  }

  public void compareXMLStructureOfRbelElement(
      final RbelElement el, final String oracle, final String diffOptionCSV) {
    assertThat(el.hasFacet(RbelXmlFacet.class))
        .withFailMessage("Node " + el.getKey() + " is not XML")
        .isTrue();
    compareXMLStructure(el.getRawStringContent(), oracle, diffOptionCSV);
  }

  public RbelElement findElementInCurrentResponse(final String rbelPath) {
    try {
      assertCurrentResponseFound();
      final List<RbelElement> elems = currentResponse.findRbelPathMembers(rbelPath);
      assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
      assertThat(elems)
          .withFailMessage("Expected exactly one match for path '" + rbelPath + "'!")
          .hasSize(1);
      return elems.get(0);
    } catch (final Exception e) {
      throw new AssertionError(
          "Unable to find element in last response for rbel path '"
              + rbelPath
              + printMessageTree(currentResponse));
    }
  }

  private static @NotNull String printMessageTree(RbelElement msg) {
    return "' in message\n " + msg.printTreeStructure();
  }

  private void assertCurrentResponseFound() {
    if (currentResponse == null) {
      throw new AssertionError("No current response message found!");
    }
  }

  public RbelElement findElementInCurrentRequest(final String rbelPath) {
    try {
      assertCurrentRequestFound();
      final List<RbelElement> elems = currentRequest.findRbelPathMembers(rbelPath);
      if (elems.size() != 1) {
        log.atWarn()
            .addArgument(rbelPath)
            .addArgument(currentRequest::printTreeStructure)
            .log("Could not find elements {} in message\n {}");
      }
      assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
      assertThat(elems)
          .withFailMessage("Expected exactly one match for path '" + rbelPath + "'!")
          .hasSize(1);
      return elems.get(0);
    } catch (final Exception e) {
      throw new AssertionError(
          "Unable to find element in last request for rbel path '"
              + rbelPath
              + printMessageTree(currentRequest));
    }
  }

  public List<RbelElement> findElementsInCurrentResponse(final String rbelPath) {
    try {
      assertCurrentResponseFound();
      final List<RbelElement> elems = currentResponse.findRbelPathMembers(rbelPath);
      assertThat(elems).isNotEmpty();
      return elems;
    } catch (final Exception e) {
      throw new AssertionError(
          "Unable to find element in last response for rbel path '"
              + rbelPath
              + printMessageTree(currentResponse));
    }
  }

  public List<RbelElement> findElementsInCurrentRequest(final String rbelPath) {
    try {
      assertCurrentRequestFound();
      final List<RbelElement> elems = currentRequest.findRbelPathMembers(rbelPath);
      assertThat(elems).isNotEmpty();
      return elems;
    } catch (final Exception e) {
      throw new AssertionError(
          "Unable to find element in request for rbel path '"
              + rbelPath
              + printMessageTree(currentRequest));
    }
  }

  private void assertCurrentRequestFound() {
    if (currentRequest == null) {
      throw new AssertionError("No current request message found!");
    }
  }

  public void findAnyMessageMatchingAtNode(String rbelPath, String value) {
    if (getRbelMessages().stream()
        .map(
            msg -> {
              List<RbelElement> findings = new RbelPathExecutor<>(msg, rbelPath).execute();

              if (findings.isEmpty()) {
                return null;
              } else {
                return getValueOrContentString(findings.get(0));
              }
            })
        .filter(Objects::nonNull)
        .filter(msg -> msg.equals(value))
        .findAny()
        .isEmpty()) {
      throw new AssertionError(
          "No message with matching value '" + value + "' at path '" + rbelPath + "'");
    }
  }

  public void findLastRequest() {
    final Iterator<RbelElement> descendingIterator = new ReverseListIterator<>(getRbelMessages());
    final RbelElement lastRequest =
        StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(descendingIterator, Spliterator.ORDERED), false)
            .filter(msg -> msg.hasFacet(RbelRequestFacet.class))
            .findFirst()
            .orElseThrow(() -> new TigerLibraryException("No Request found."));
    setCurrentRequest(lastRequest);
    setCurrentResponse(
        lastRequest
            .getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getResponse)
            .orElse(null));
  }

  public void readTgrFile(String filePath) {
    List<RbelElement> readElements = tigerProxy.readTrafficFromTgrFile(filePath);
    readElements.forEach(LocalProxyRbelMessageListener.getInstance()::triggerNewReceivedMessage);
  }

  public class JexlToolbox {

    public String currentResponseAsString(final String rbelPath) {
      return findElementInCurrentResponse(rbelPath).getRawStringContent();
    }

    public String currentResponseAsString() {
      return currentResponse.getRawStringContent();
    }

    public RbelElement currentResponse(final String rbelPath) {
      return findElementInCurrentResponse(rbelPath);
    }

    public String currentRequestAsString(final String rbelPath) {
      return getValueOrContentString(findElementInCurrentRequest(rbelPath));
    }

    public String currentRequestAsString() {
      return currentRequest.getRawStringContent();
    }

    public RbelElement currentRequest(final String rbelPath) {
      return findElementInCurrentRequest(rbelPath);
    }

    public RbelElement lastResponse() {
      return lastMessageMatching(
          msg ->
              msg.hasFacet(RbelResponseFacet.class) || msg.hasFacet(RbelHttpResponseFacet.class));
    }

    public String lastResponseAsString() {
      return Optional.ofNullable(lastResponse())
          .map(RbelElement::getRawStringContent)
          .orElseThrow(NoSuchElementException::new);
    }

    public RbelElement lastRequest() {
      return lastMessageMatching(
          msg -> msg.hasFacet(RbelRequestFacet.class) || msg.hasFacet(RbelHttpRequestFacet.class));
    }

    private RbelElement lastMessageMatching(Predicate<RbelElement> testMessage) {
      final Iterator<RbelElement> backwardsIterator =
          LocalProxyRbelMessageListener.getInstance()
              .getValidatableRbelMessages()
              .descendingIterator();
      while (backwardsIterator.hasNext()) {
        final RbelElement element = backwardsIterator.next();
        if (testMessage.test(element)) {
          return element;
        }
      }
      throw new NoSuchElementException();
    }

    public String lastRequestAsString() {
      return Optional.ofNullable(lastRequest())
          .map(RbelElement::getRawStringContent)
          .orElseThrow(NoSuchElementException::new);
    }

    public String getValueAtLocationAsString(RbelElement element, String rbelPath) {
      return element
          .findElement(rbelPath)
          .flatMap(el -> el.getFacet(RbelValueFacet.class))
          .map(RbelValueFacet::getValue)
          .map(Object::toString)
          .orElseThrow(
              () ->
                  new NoSuchElementException(
                      "Unable to find a matching element for '" + rbelPath + "'"));
    }

    /**
     * Encodes the string explicitly in the given content type.
     *
     * <p>When used inline in a JEXL expression, the valueToEncode must be the raw string output
     * from either `getValue()` or read directly from `file()`.
     *
     * @param valueToEncode the string to encode
     * @param contentType a string matching one of the {@link RbelContentType}s enum values
     * @return the encoded string.
     */
    public String encodeAs(String valueToEncode, String contentType) {
      val encodeAs = RbelContentType.seekValueFor(contentType);
      RbelLoggerWriter rbelLoggerWriter = new RbelLoggerWriter();
      RbelElement toEncode =
          rbelLoggerWriter.getRbelConverter().convertElement(valueToEncode, null);

      return rbelLoggerWriter
          .getRbelWriter()
          .serializeWithEnforcedContentType(toEncode, encodeAs, new TigerJexlContext())
          .getContentAsString();
    }
  }

  public static String getValueOrContentString(RbelElement elem) {
    return elem.printValue().orElseGet(elem::getRawStringContent);
  }
}
