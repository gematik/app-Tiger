/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.google.common.collect.Lists;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.util.RbelFileWriter;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.enums.ModeType;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.xml.transform.Source;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.iterators.ReverseListIterator;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.core.ConditionTimeoutException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

@SuppressWarnings("unused")
@Slf4j
public class RbelMessageValidator {

    RbelLogger rbelLogger;
    RbelFileWriter rbelFileWriter;

    private final AtomicBoolean fileParsedCompletely = new AtomicBoolean(false);


    public final static RbelMessageValidator instance = new RbelMessageValidator();
    private static final TigerTypedConfigurationKey<Integer> RBEL_REQUEST_TIMEOUT =
        new TigerTypedConfigurationKey<>("tiger.rbel.request.timeout", Integer.class);

    private static final Map<String, Function<DiffBuilder, DiffBuilder>> diffOptionMap = new HashMap<>();

    static {
        diffOptionMap.put("nocomment", DiffBuilder::ignoreComments);
        diffOptionMap.put("txtignoreempty", DiffBuilder::ignoreElementContentWhitespace);
        diffOptionMap.put("txttrim", DiffBuilder::ignoreWhitespace);
        diffOptionMap.put("txtnormalize", DiffBuilder::normalizeWhitespace);
    }

    private static final List<String> emptyPath = List.of("", "/");

    @Getter
    protected RbelElement currentRequest;
    @Getter
    protected RbelElement currentResponse;

    private RbelMessageValidator() {
        TigerJexlExecutor.registerAdditionalNamespace("rbel", new JexlToolbox());
    }

    public List<RbelElement> getRbelMessages() {
        return new UnmodifiableList<>(new ArrayList<>(LocalProxyRbelMessageListener.getValidatableRbelMessages()));
    }

    public void clearRbelMessages() {
        LocalProxyRbelMessageListener.getValidatableRbelMessages().clear();
    }

    public void filterRequestsAndStoreInContext(final RequestParameter requestParameter) {
        final int waitsec = RBEL_REQUEST_TIMEOUT.getValue().orElse(5);
        currentRequest = findMessageByDescription(requestParameter);
        try {
            await("Waiting for matching response").atMost(waitsec, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution() ||
                    getRbelMessages().stream()
                    .filter(e -> e.hasFacet(RbelHttpResponseFacet.class))
                    .filter(
                        resp -> resp.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()
                            == currentRequest)
                    .peek(rbelElement -> currentResponse = rbelElement)
                    .findAny()
                    .isPresent());
            if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
                throw new AssertionError("User aborted test run");
            }
        } catch (final ConditionTimeoutException cte) {
            log.error("Missing response message to filtered request!\n\n{}", currentRequest.getRawStringContent());
            throw new TigerLibraryException("Missing response message to filtered request!", cte);
        }
    }

    public RbelElement waitForMessageToBePresent(final RequestParameter requestParameter) {
        return findMessageByDescription(requestParameter);
    }

    protected RbelElement findMessageByDescription(final RequestParameter requestParameter) {
        final int waitsec = RBEL_REQUEST_TIMEOUT.getValue().orElse(5);

        Optional<RbelElement> initialElement = getInitialElement(requestParameter);

        final AtomicReference<RbelElement> candidate = new AtomicReference<>();
        try {
            await("Waiting for matching request").atMost(waitsec, TimeUnit.SECONDS)
                .pollDelay(0, TimeUnit.SECONDS).pollInterval(400, TimeUnit.MILLISECONDS)
                .until(() -> {
                    if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
                        return true;
                    }
                    final Optional<RbelElement> found = filterRequests(requestParameter, initialElement);
                    found.ifPresent(candidate::set);
                    return found.isPresent();
                });
                if (TigerDirector.getTigerTestEnvMgr().isShouldAbortTestExecution()) {
                    throw new AssertionError("User aborted test run");
                }
        } catch (final ConditionTimeoutException cte) {
            log.error("Didn't find any matching messages!");
            printAllPathsOfMessages(getRbelMessages());
            if (requestParameter.getPath() == null) {
                throw new AssertionError(
                    "No request with matching rbelPath '" + requestParameter.getRbelPath() + "' found in messages");
            } else if (requestParameter.getRbelPath() == null) {
                throw new AssertionError(
                    "No request with path '" + requestParameter.getPath() + "' found in messages");
            } else {
                throw new AssertionError(
                    "No request with path '" + requestParameter.getPath() + "' and rbelPath '"
                        + requestParameter.getRbelPath()
                        + "' matching '" + StringUtils.abbreviate(requestParameter.getValue(), 300)
                        + "' found in messages");
            }
        }
        return candidate.get();
    }

    private Optional<RbelElement> getInitialElement(RequestParameter requestParameter) {
        if (requestParameter.isStartFromLastRequest()) {
            return LocalProxyRbelMessageListener.getValidatableRbelMessages().stream()
                .dropWhile(msg -> msg != currentRequest)
                .skip(1)
                .findFirst();
        } else if (requestParameter.isRequireNewMessage() && !LocalProxyRbelMessageListener.getValidatableRbelMessages().isEmpty()) {
            return Optional.ofNullable(LocalProxyRbelMessageListener.getValidatableRbelMessages().getLast());
        } else {
            return Optional.empty();
        }
    }

    protected Optional<RbelElement> filterRequests(final RequestParameter requestParameter, Optional<RbelElement> startFromMessageExclusively) {
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
                msgs = new ArrayList<>(msgs.subList(idx + 1, msgs.size()));
            }
        }
        final String hostFilter = TigerGlobalConfiguration.readString("tiger.rbel.request.filter.host", "");
        final String methodFilter = TigerGlobalConfiguration.readString("tiger.rbel.request.filter.method", "");

        List<RbelElement> candidateMessages = msgs.stream()
            .filter(el -> !requestParameter.isRequireHttpMessage() || el.hasFacet(RbelHttpRequestFacet.class))
            .filter(req -> doesPathOfMessageMatch(req, requestParameter.getPath()))
            .filter(req -> hostFilter == null || hostFilter.isEmpty() || doesHostMatch(req, hostFilter))
            .filter(req -> methodFilter == null || methodFilter.isEmpty() || doesMethodMatch(req, methodFilter))
            .toList();
        if (candidateMessages.isEmpty()) {
            return Optional.empty();
        }

        if (StringUtils.isEmpty(requestParameter.getRbelPath())) {
            if (candidateMessages.size() > 1) {
                String warnMsg = requestParameter.isFilterPreviousRequest() ? "last" : "first";
                log.warn("Found more then one candidate message. "
                    + "Returning " + warnMsg + " message. This may not be deterministic!");
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

        for (final RbelElement candidateMessage : candidateMessages) {
            final List<RbelElement> pathExecutionResult = new RbelPathExecutor(candidateMessage,
                requestParameter.getRbelPath()).execute();
            if (pathExecutionResult.isEmpty()) {
                continue;
            }
            if (StringUtils.isEmpty(requestParameter.getValue())) {
                return Optional.of(candidateMessage);
            } else {
                final String content = pathExecutionResult.stream()
                    .map(this::getValueOrContentString)
                    .map(String::trim)
                    .collect(Collectors.joining());
                try {
                    if (content.equals(requestParameter.getValue()) ||
                        content.matches(requestParameter.getValue()) ||
                        Pattern.compile(requestParameter.getValue(), Pattern.DOTALL).matcher(content).matches()) {
                        return Optional.of(candidateMessage);
                    } else {
                        log.info("Found rbel node but \n'" + StringUtils.abbreviate(content, 300) + "' didnt match\n'"
                            + StringUtils.abbreviate(requestParameter.getValue(), 300) + "'");
                    }
                } catch (final Exception ex) {
                    log.error("Failure while trying to apply regular expression '" + requestParameter.getValue() + "'!",
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
            final URI uri = new URI(req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(this::getValueOrContentString)
                .orElse(""));
            boolean match = uri.getPath().equals(path) || uri.getPath().matches(path);
            if (!match && emptyPath.contains(path) && emptyPath.contains(uri.getPath())) {
                match = true;
            }
            return match;
        } catch (final URISyntaxException e) {
            return false;
        } catch (final PatternSyntaxException rte) {
            log.error("Error while parsing regex!", rte);
            return false;
        }
    }

    public static boolean doesHostMatch(final RbelElement req, final String hostFilter) {
        try {
            final String host = req.getFacet(RbelHttpMessageFacet.class)
                .map(RbelHttpMessageFacet::getHeader)
                .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
                .map(el -> el.get("Host"))
                .map(RbelElement::getRawStringContent)
                .orElse("");
            return StringUtils.equals(host, hostFilter) || host.matches(hostFilter);
        } catch (final RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public boolean doesMethodMatch(final RbelElement req, final String method) {
        try {
            final String reqMethod = req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getMethod)
                .map(RbelElement::getRawStringContent)
                .map(String::toUpperCase)
                .orElse("");
            return method.equals(reqMethod) || method.matches(reqMethod);
        } catch (final RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public void assertAttributeOfCurrentResponseMatches(final String rbelPath, final String value,
        boolean shouldMatch) {
        final String text = findElementsInCurrentResponse(rbelPath).stream()
            .map(this::getValueOrContentString)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.joining());
        if (shouldMatch) {
            if (!text.equals(value)) {
                assertThat(text).as("Rbelpath '%s' matches", rbelPath)
                    .matches(Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL));
            }
        } else {
            if (text.equals(value)) {
                Assertions.fail("Did not expect that node '" + rbelPath + "' is equal to '" + value);
            }
            assertThat(text).as("Rbelpath '%s' does not match", rbelPath)
                .doesNotMatch(Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL));
        }
    }

    private String getValueOrContentString(RbelElement elem) {
        return elem.printValue().orElseGet(elem::getRawStringContent);
    }

    public void assertAttributeOfCurrentResponseMatchesAs(String rbelPath, ModeType mode, String oracle) {
        switch (mode) {
            case JSON:
                new JsonChecker().compareJsonStrings(
                    getValueOrContentString(findElementInCurrentResponse(rbelPath)),
                    oracle,
                    false);
                break;
            case XML:
                final RbelElement el = findElementInCurrentResponse(rbelPath);
                compareXMLStructureOfRbelElement(el, oracle, "");
                break;
            default:
                Assertions.fail("Type should either be JSON or XML, but you wrote '" + mode + "' instead.");
                break;
        }
    }

    private void printAllPathsOfMessages(final List<RbelElement> msgs) {
        long requests = msgs.stream().filter(msg -> msg.getFacet(RbelHttpRequestFacet.class).isPresent()).count();
        log.info("Found the following {} messages:\n{} ", requests, msgs.stream()
            .map(msg -> msg.getFacet(RbelHttpRequestFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(req -> "=>\t" + req.getPathAsString() + " : " + req.getChildElements())
            .collect(Collectors.joining("\n")));
    }

    public void compareXMLStructure(final String test, final String oracle,
        final List<Function<DiffBuilder, DiffBuilder>> diffOptions) {
        final ArrayList<Difference> diffs = new ArrayList<>();
        final Source srcTest = Input.from(test).build();
        final Source srcOracle = Input.from(oracle).build();
        DiffBuilder db = DiffBuilder.compare(srcOracle).withTest(srcTest);
        for (final Function<DiffBuilder, DiffBuilder> src : diffOptions) {
            db = src.apply(db);
        }
        db = db.checkForSimilar();
        db.withDifferenceEvaluator((comparison, outcome) -> {
            if (outcome != ComparisonResult.EQUAL &&
                (comparison.getType() == ComparisonType.NAMESPACE_URI
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
    public void compareXMLStructure(final String test, final String oracle, final String diffOptionCSV) {
        final List<Function<DiffBuilder, DiffBuilder>> diffOptions = new ArrayList<>();
        Arrays.stream(diffOptionCSV.split(","))
            .map(String::trim)
            .forEach(srcClassId -> {
                assertThat(diffOptionMap).containsKey(srcClassId);
                diffOptions.add(diffOptionMap.get(srcClassId));
            });
        compareXMLStructure(test, oracle, diffOptions);
    }

    public void compareXMLStructureOfRbelElement(final RbelElement el, final String oracle,
        final String diffOptionCSV) {
        assertThat(el.hasFacet(RbelXmlFacet.class)).withFailMessage("Node " + el.getKey() + " is not XML").isTrue();
        compareXMLStructure(el.getRawStringContent(), oracle, diffOptionCSV);
    }

    public RbelElement findElementInCurrentResponse(final String rbelPath) {
        try {
            final List<RbelElement> elems = currentResponse.findRbelPathMembers(rbelPath);
            assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
            assertThat(elems).withFailMessage("Expected exactly one match for path '" + rbelPath + "'!").hasSize(1);
            return elems.get(0);
        } catch (final Exception e) {
            throw new AssertionError("Unable to find element in last response for rbel path '" + rbelPath + "'");
        }
    }

    public RbelElement findElementInCurrentRequest(final String rbelPath) {
        try {
            final List<RbelElement> elems = currentRequest.findRbelPathMembers(rbelPath);
            if (elems.size() != 1) {
                log.warn("Could not find elements {} in message\n {}", rbelPath, currentRequest.printTreeStructureWithoutColors());
            }
            assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
            assertThat(elems).withFailMessage("Expected exactly one match for path '" + rbelPath + "'!").hasSize(1);
            return elems.get(0);
        } catch (final Exception e) {
            throw new AssertionError("Unable to find element in last request for rbel path '" + rbelPath + "'");
        }
    }

    public List<RbelElement> findElementsInCurrentResponse(final String rbelPath) {
        try {
            final List<RbelElement> elems = currentResponse.findRbelPathMembers(rbelPath);
            assertThat(elems).isNotEmpty();
            return elems;
        } catch (final Exception e) {
            throw new AssertionError("Unable to find element in last response for rbel path '" + rbelPath + "'");
        }
    }

    public void findAnyMessageMatchingAtNode(String rbelPath, String value) {
        if (getRbelMessages().stream()
            .map(msg -> {
                List<RbelElement> findings = new RbelPathExecutor(msg, rbelPath).execute();

                if (findings.isEmpty()) {
                    return null;
                } else {
                    return getValueOrContentString(findings.get(0));
                }
            })
            .filter(Objects::nonNull)
            .filter(msg -> msg.equals(value))
            .findAny().isEmpty()) {
            throw new AssertionError(
                "No message with matching value '" + value + "' at path '" + rbelPath + "'");
        }
    }

    public void findLastRequest() {
        final Iterator<RbelElement> descendingIterator = new ReverseListIterator(getRbelMessages());
        final RbelElement lastRequest = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(descendingIterator, Spliterator.ORDERED), false)
            .filter(msg -> msg.hasFacet(RbelRequestFacet.class))
            .findFirst()
            .orElseThrow(() -> new TigerLibraryException("No Request found."));
        this.currentRequest = lastRequest;
        this.currentResponse = lastRequest.getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getResponse)
            .orElse(null);
    }

    public void readTgrFile(String filePath) {
        if (TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOptional().isPresent()) {
            List<RbelElement> readElements = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail()
                .readTrafficFromTgrFile(filePath);
            readElements.forEach(LocalProxyRbelMessageListener.rbelMessageListener::triggerNewReceivedMessage);
        } else {
            throw new TigerTestEnvException("No local proxy active, can't read from tgr file '" + filePath + "'");
        }
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
            return lastMessageMatching(msg ->
                msg.hasFacet(RbelResponseFacet.class) || msg.hasFacet(RbelHttpResponseFacet.class));
        }

        public String lastResponseAsString() {
            return Optional.ofNullable(lastResponse())
                .map(RbelElement::getRawStringContent)
                .orElseThrow(NoSuchElementException::new);
        }

        public RbelElement lastRequest() {
            return lastMessageMatching(msg ->
                msg.hasFacet(RbelRequestFacet.class) || msg.hasFacet(RbelHttpRequestFacet.class));
        }

        private RbelElement lastMessageMatching(Predicate<RbelElement> testMessage) {
            final Iterator<RbelElement> backwardsIterator = LocalProxyRbelMessageListener.getValidatableRbelMessages().descendingIterator();
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
            return element.findElement(rbelPath)
                .flatMap(el -> el.getFacet(RbelValueFacet.class))
                .map(RbelValueFacet::getValue)
                .map(Object::toString)
                .orElseThrow(() -> new NoSuchElementException("Unable to find a matching element for '" + rbelPath + "'"));
        }
    }
}
