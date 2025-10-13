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
package de.gematik.test.tiger.lib.rbel;

import static de.gematik.rbellogger.data.core.RbelMismatchNoteFacet.MismatchType.*;
import static org.awaitility.Awaitility.await;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.RbelLoggerWriter;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.exception.ValidatorAssertionError;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.iterators.ReverseListIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class RbelMessageRetriever {

  public static final String RBEL_NAMESPACE = "rbel";
  public static final String FOUND_IN_MESSAGES = "' found in messages";

  private static final List<String> EMPTY_PATH = List.of("", "/");
  public static final TigerTypedConfigurationKey<Integer> RBEL_REQUEST_TIMEOUT =
      new TigerTypedConfigurationKey<>("tiger.rbel.request.timeout", Integer.class, 5);

  private static RbelMessageRetriever instance;

  public static RbelMessageRetriever getInstance() {
    // In unit tests when we reset the tiger test environment ( see
    // de.gematik.test.tiger.lib.TigerDirector.testUninitialize )
    // we set the instance to null, to force the recreation of the RbelMessageRetriever. Otherwise
    // the instance will keep references
    // to a discarded tigerTestEnvMgr and tigerProxy and not see the messages of the current
    // environment.
    synchronized (RbelMessageRetriever.class) {
      if (instance == null) { // some other thread might be
        instance = new RbelMessageRetriever();
      }
      // in case some other entity instantiated a RbelMessageRetriever
      instance.registerJexlToolbox();
    }
    return instance;
  }

  @VisibleForTesting
  public static synchronized void clearInstance() {
    instance = null;
    TigerJexlExecutor.deregisterNamespace(RBEL_NAMESPACE);
  }

  @Getter private final TigerTestEnvMgr tigerTestEnvMgr;
  private final TigerProxy tigerProxy;

  @Getter private final LocalProxyRbelMessageListener localProxyRbelMessageListener;

  @Setter @Getter protected RbelElement currentRequest;

  // contains either currentRequest or currentResponse
  private RbelElement lastFoundMessage;

  @Setter(AccessLevel.PROTECTED)
  @Getter
  protected RbelElement currentResponse;

  private RbelMessageRetriever() {
    this(
        TigerDirector.getTigerTestEnvMgr(),
        TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail());
  }

  /**
   * @deprecated This constructor is due to be removed. Please use the constructor with the
   *     additional parameter instead.
   */
  @SuppressWarnings("java:S1133")
  @Deprecated(forRemoval = true)
  public RbelMessageRetriever(TigerTestEnvMgr tigerTestEnvMgr, TigerProxy tigerProxy) {
    this(tigerTestEnvMgr, tigerProxy, LocalProxyRbelMessageListener.getInstance());
  }

  public RbelMessageRetriever(
      TigerTestEnvMgr tigerTestEnvMgr,
      TigerProxy tigerProxy,
      LocalProxyRbelMessageListener localProxyRbelMessageListener) {
    registerJexlToolbox();
    this.tigerTestEnvMgr = tigerTestEnvMgr;
    this.tigerProxy = tigerProxy;
    this.localProxyRbelMessageListener = localProxyRbelMessageListener;
  }

  private void registerJexlToolbox() {
    TigerJexlExecutor.registerAdditionalNamespace(
        RBEL_NAMESPACE, new RbelMessageRetriever.JexlToolbox());
  }

  public List<RbelElement> getRbelMessages() {
    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    return localProxyRbelMessageListener.getValidatableRbelMessages().stream().toList();
  }

  public void clearRbelMessages() {
    localProxyRbelMessageListener.clearValidatableRbelMessages();
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
        final int requestTimeout = RBEL_REQUEST_TIMEOUT.getValueOrDefault();
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
    final int waitsec = RBEL_REQUEST_TIMEOUT.getValueOrDefault();

    Optional<RbelElement> initialElement = getInitialElement(requestParameter);
    val startAfterMessage = new AtomicReference<RbelElement>(); // avoid double checks
    val mismatchNotes = new HashMap<RbelElement, SortedSet<RbelMismatchNoteFacet>>();
    val candidate = new AtomicReference<RbelElement>();

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
                    findMessage(requestParameter, initialElement, startAfterMessage, mismatchNotes);
                found.ifPresent(candidate::set);
                return found.isPresent();
              });
      if (tigerTestEnvMgr.isShouldAbortTestExecution()) {
        throw new AssertionError("User aborted test run");
      }
    } catch (final ConditionTimeoutException cte) {
      log.error("Didn't find any matching messages!");
      printAllPathsOfMessages(getRbelMessages());
      String message;
      if (requestParameter.getPath() == null) {
        message =
            String.format(
                "No request with matching rbelPath '%s%s",
                requestParameter.getRbelPath(), FOUND_IN_MESSAGES);
      } else if (requestParameter.getRbelPath() == null) {
        message =
            String.format(
                "No request with path '%s%s", requestParameter.getPath(), FOUND_IN_MESSAGES);
      } else {
        message =
            String.format(
                "No request with path '%s' and rbelPath '%s' matching '%s%s",
                requestParameter.getPath(),
                requestParameter.getRbelPath(),
                StringUtils.abbreviate(requestParameter.getValue(), 300),
                FOUND_IN_MESSAGES);
      }
      throw buildValidatorError(message, mismatchNotes);
    }
    return candidate.get();
  }

  private static @NotNull AssertionError buildValidatorError(
      String message, Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    return mismatchNotes.isEmpty()
        ? new AssertionError(message)
        : new ValidatorAssertionError(message, mismatchNotes);
  }

  private Optional<RbelElement> getInitialElement(RequestParameter requestParameter) {
    var validatableRbelMessages = localProxyRbelMessageListener.getValidatableRbelMessages();
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

  protected Optional<RbelElement> findMessage(
      final RequestParameter requestParameter,
      Optional<RbelElement> startFromMessageInclusively,
      AtomicReference<RbelElement> startAfterMessage,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    List<RbelElement> msgs =
        getRbelElementsOptionallyFromGivenMessageInclusively(
            startFromMessageInclusively, startAfterMessage);
    final String hostFilter = TigerConfigurationKeys.REQUEST_FILTER_HOST.getValueOrDefault();
    final String methodFilter = TigerConfigurationKeys.REQUEST_FILTER_METHOD.getValueOrDefault();

    List<RbelElement> candidateMessages =
        getCandidateMessages(requestParameter, msgs, hostFilter, methodFilter, mismatchNotes);
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
      val candidate = getRequiredCandidate(requestParameter, candidateMessages);
      addMismatchNotesForOtherCandidates(mismatchNotes, candidateMessages, candidate);
      return Optional.of(candidate);
    }

    if (requestParameter.isFilterPreviousRequest()) {
      candidateMessages = Lists.reverse(candidateMessages);
    }

    return findFirstMatchingMessage(requestParameter, candidateMessages, mismatchNotes);
  }

  private static void addMismatchNotesForOtherCandidates(
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes,
      List<RbelElement> candidateMessages,
      RbelElement candidate) {
    for (var candidateMessage : candidateMessages) {
      if (candidateMessage != candidate) {
        addMismatchNote(
            mismatchNotes,
            candidateMessage,
            AMBIGUOUS,
            "Possible candidate message (dependent on given path, host filter and method), but"
                + " not used");
      }
    }
  }

  private static RbelElement getRequiredCandidate(
      RequestParameter requestParameter, List<RbelElement> candidateMessages) {
    if (requestParameter.isFilterPreviousRequest()) {
      return candidateMessages.get(candidateMessages.size() - 1);
    } else {
      return candidateMessages.get(0);
    }
  }

  private List<RbelElement> getRbelElementsOptionallyFromGivenMessageInclusively(
      Optional<RbelElement> startFromMessageExclusively, AtomicReference<RbelElement> lastChecked) {
    List<RbelElement> msgs = getRbelMessages();
    var lastCheckedMessage = lastChecked.get();
    if (lastCheckedMessage != null) {
      // we have already searched for messages before
      for (int i = msgs.size() - 1; i >= 0; i--) { // probably few new messages, so start at the end
        if (msgs.get(i) == lastCheckedMessage) {
          lastChecked.set(msgs.get(msgs.size() - 1));
          return new ArrayList<>(msgs.subList(i + 1, msgs.size()));
        }
      }
      return msgs; // last checked message not found, all messages are new
    }
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
    if (!msgs.isEmpty()) {
      lastChecked.set(msgs.get(msgs.size() - 1));
    }
    return msgs;
  }

  @NotNull
  private List<RbelElement> getCandidateMessages(
      RequestParameter requestParameter,
      List<RbelElement> msgs,
      String hostFilter,
      String methodFilter,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    return msgs.stream()
        .filter(
            el ->
                !requestParameter.isRequireRequestMessage() || el.hasFacet(RbelRequestFacet.class))
        .filter(req -> pathMatches(req, requestParameter.getPath(), mismatchNotes))
        .filter(req -> hostFilterMatches(req, hostFilter, mismatchNotes))
        .filter(req -> methodFilterMatches(req, methodFilter, mismatchNotes))
        .toList();
  }

  private boolean pathMatches(
      RbelElement req,
      String path,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    if (path == null) {
      return true;
    }
    try {
      final URI uri = getHttpRequestPath(req);
      if (doesPathOfMessageMatch(uri.getPath(), path)) {
        return true;
      } else {
        addMismatchNote(
            mismatchNotes,
            req,
            WRONG_PATH,
            String.format("Path '%s' didn't match '%s'!", uri.getPath(), path));
        return false;
      }
    } catch (URISyntaxException e) {
      addMismatchNote(
          mismatchNotes,
          req,
          WRONG_PATH,
          String.format("Path '%s' couldn't be parsed as URI!", path));
      return false;
    }
  }

  private boolean hostFilterMatches(
      RbelElement req,
      String hostFilter,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    if (hostFilter == null || hostFilter.isEmpty() || doesHostMatch(req, hostFilter)) {
      return true;
    } else {
      addMismatchNote(
          mismatchNotes,
          req,
          FILTER_MISMATCH,
          String.format("Host '%s' didn't match!", hostFilter));
      return false;
    }
  }

  private boolean methodFilterMatches(
      RbelElement req,
      String methodFilter,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    if (methodFilter == null || methodFilter.isEmpty() || doesMethodMatch(req, methodFilter)) {
      return true;
    } else {
      addMismatchNote(
          mismatchNotes,
          req,
          FILTER_MISMATCH,
          String.format("Method '%s' didn't match!", methodFilter));
      return false;
    }
  }

  private static void addMismatchNote(
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes,
      RbelElement req,
      RbelMismatchNoteFacet.MismatchType mismatchType,
      String note) {
    mismatchNotes
        .computeIfAbsent(req, k -> new TreeSet<>(RbelMismatchNoteFacet.COMPARATOR))
        .add(new RbelMismatchNoteFacet(mismatchType, note, req));
  }

  @NotNull
  private Optional<RbelElement> findFirstMatchingMessage(
      RequestParameter requestParameter,
      List<RbelElement> candidateMessages,
      Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes) {
    val rbelPath = requestParameter.getRbelPath();
    val expectedValue = requestParameter.getValue();
    for (val candidateMessage : candidateMessages) {
      val nodesAtGivenPath = candidateMessage.findRbelPathMembers(rbelPath);
      if (nodesAtGivenPath.isEmpty()) {
        addMismatchNote(
            mismatchNotes,
            candidateMessage,
            MISSING_NODE,
            String.format("No node found for rbelPath '%s'!", rbelPath));
        continue;
      }
      if (StringUtils.isEmpty(expectedValue)) {
        return Optional.of(candidateMessage);
      } else {
        val content =
            nodesAtGivenPath.stream()
                .map(RbelMessageRetriever::getValueOrContentString)
                .map(String::trim)
                .collect(Collectors.joining());
        try {
          if (content.equals(expectedValue)
              || content.matches(expectedValue)
              || Pattern.compile(expectedValue, Pattern.DOTALL).matcher(content).matches()) {
            return Optional.of(candidateMessage);
          } else {
            log.atTrace()
                .addArgument(() -> StringUtils.abbreviate(content, 300))
                .addArgument(() -> StringUtils.abbreviate(expectedValue, 300))
                .log("Found rbel node but \n'{}' didnt match\n'{}'");
            nodesAtGivenPath.forEach(
                node ->
                    addMismatchNote(
                        mismatchNotes,
                        node,
                        VALUE_MISMATCH,
                        String.format(
                            "Mismatch at rbelPath '%s':%n'%s' didn't match expected%n'%s'",
                            rbelPath, content, expectedValue)));
          }
        } catch (final Exception ex) {
          log.error("Failure while trying to apply regular expression '{}'!", expectedValue, ex);
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
      return doesPathOfMessageMatch(getHttpRequestPath(req).getPath(), path);
    } catch (final URISyntaxException e) {
      return false;
    }
  }

  private static @NotNull URI getHttpRequestPath(RbelElement req) throws URISyntaxException {
    final URI uri =
        new URI(
            req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelMessageRetriever::getValueOrContentString)
                .orElse(""));
    return uri;
  }

  private boolean doesPathOfMessageMatch(String pathValue, String pathExpression) {
    boolean match = doesItMatch(pathValue, pathExpression);
    if (!match && EMPTY_PATH.contains(pathExpression) && EMPTY_PATH.contains(pathValue)) {
      match = true;
    }
    return match;
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

  public RbelElement findElementInCurrentResponse(final String rbelPath) {
    try {
      assertCurrentResponseFound();
      return findElementInMessage(rbelPath, currentResponse);
    } catch (final Exception e) {
      val note =
          new RbelMismatchNoteFacet(
              UNKNOWN,
              "Unable to find element in last response for rbel path '" + rbelPath + "'",
              currentResponse);
      throw new ValidatorAssertionError(
          "Unable to find element in last response for rbel path '"
              + rbelPath
              + printMessageTree(currentResponse),
          mismatchNotes(currentResponse, note));
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
      return findElementInMessage(rbelPath, currentRequest);
    } catch (final Exception e) {
      val note =
          new RbelMismatchNoteFacet(
              UNKNOWN,
              "Unable to find element in last request for rbel path '" + rbelPath + "'",
              currentRequest);
      throw new ValidatorAssertionError(
          "Unable to find element in last request for rbel path '"
              + rbelPath
              + printMessageTree(currentRequest),
          mismatchNotes(currentResponse, note));
    }
  }

  private static RbelElement findElementInMessage(String rbelPath, RbelElement message) {
    val elems = message.findRbelPathMembers(rbelPath);
    if (elems.size() != 1) {
      log.atWarn()
          .addArgument(elems.size())
          .addArgument(rbelPath)
          .addArgument(message::printTreeStructure)
          .log("Found {} elements with path {} in message:\n{}");
    }
    String errorMsg;
    RbelMismatchNoteFacet.MismatchType mismatchType;
    if (elems.isEmpty()) {
      errorMsg = "No node matching path '" + rbelPath + "'!";
      mismatchType = MISSING_NODE;
    } else if (elems.size() > 1) {
      errorMsg = "Expected exactly one match for path '" + rbelPath + "'!";
      mismatchType = AMBIGUOUS;
    } else {
      return elems.get(0);
    }
    val note = new RbelMismatchNoteFacet(mismatchType, errorMsg, message);
    throw new ValidatorAssertionError(errorMsg, mismatchNotes(message, note));
  }

  private static @NotNull Map<RbelElement, SortedSet<RbelMismatchNoteFacet>> mismatchNotes(
      RbelElement message, RbelMismatchNoteFacet note) {
    return Map.of(message, new TreeSet<>(List.of(note)));
  }

  public List<RbelElement> findElementsInCurrentResponse(final String rbelPath) {
    assertCurrentResponseFound();
    final List<RbelElement> elems = currentResponse.findRbelPathMembers(rbelPath);
    if (elems.isEmpty()) {
      val errorMsg = "Unable to find element in response for rbel path '" + rbelPath + "'";
      val note = new RbelMismatchNoteFacet(MISSING_NODE, errorMsg, currentResponse);
      throw new ValidatorAssertionError(
          "Unable to find element in response for rbel path '"
              + rbelPath
              + printMessageTree(currentResponse),
          mismatchNotes(currentResponse, note));
    }
    return elems;
  }

  public List<RbelElement> findElementsInCurrentRequest(final String rbelPath) {
    assertCurrentRequestFound();
    final List<RbelElement> elems = currentRequest.findRbelPathMembers(rbelPath);
    if (elems.isEmpty()) {
      val errorMsg = "Unable to find element in request for rbel path '" + rbelPath + "'";
      val note = new RbelMismatchNoteFacet(MISSING_NODE, errorMsg, currentRequest);
      throw new ValidatorAssertionError(
          "Unable to find element in request for rbel path '"
              + rbelPath
              + printMessageTree(currentRequest),
          mismatchNotes(currentRequest, note));
    }
    return elems;
  }

  private void assertCurrentRequestFound() {
    if (currentRequest == null) {
      throw new AssertionError("No current request message found!");
    }
  }

  public void findAnyMessageMatchingAtNode(String rbelPath, String value) {
    val mismatchNotes = new HashMap<RbelElement, SortedSet<RbelMismatchNoteFacet>>();
    if (getRbelMessages().stream()
        .map(
            msg -> {
              List<RbelElement> findings = msg.findRbelPathMembers(rbelPath);

              if (findings.isEmpty()) {
                addMismatchNote(
                    mismatchNotes,
                    msg,
                    MISSING_NODE,
                    String.format("No node found for rbelPath '%s'!", rbelPath));
                return null;
              } else {
                return Pair.of(msg, getValueOrContentString(findings.get(0)));
              }
            })
        .filter(Objects::nonNull)
        .filter(
            msg -> {
              String content = msg.getRight();
              if (content.equals(value)) {
                return true;
              } else {
                addMismatchNote(
                    mismatchNotes,
                    msg.getLeft(),
                    VALUE_MISMATCH,
                    String.format(
                        "Mismatch at rbelPath '%s':%n'%s' didn't match expected%n'%s'",
                        rbelPath, content, value));
                return false;
              }
            })
        .findAny()
        .isEmpty()) {
      throw buildValidatorError(
          "No message with matching value '" + value + "' at path '" + rbelPath + "'",
          mismatchNotes);
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
          localProxyRbelMessageListener.getValidatableRbelMessages().descendingIterator();
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
