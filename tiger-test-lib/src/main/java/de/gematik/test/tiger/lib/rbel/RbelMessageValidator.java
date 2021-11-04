/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.common.context.TestContext;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.hooks.TigerTestHooks;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.xml.transform.Source;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    @Getter
    protected RbelElement lastFilteredRequest;
    @Getter
    protected RbelElement lastResponse;
    @Getter
    protected TestContext context = new TestContext("tiger");

    public RbelMessageValidator() {

    }

    public List<RbelElement> getRbelMessages() {
        return TigerTestHooks.getValidatableRbelMessages();
    }

    public void clearRBelMessages() {
        TigerTestHooks.getValidatableRbelMessages().clear();
    }

    public void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
        boolean startFromLastRequest) {
        int waitsec = (int) context.getContext("tiger").computeIfAbsent("rbel.request.timeout", key -> 5);
        lastFilteredRequest = findRequestByDescription(path, rbelPath, value, startFromLastRequest);
        try {
            await("Waiting for matching response").atMost(waitsec, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .pollDelay(200, TimeUnit.MILLISECONDS)
                .until(() -> getRbelMessages().stream()
                    .filter(e -> e.hasFacet(RbelHttpResponseFacet.class))
                    .filter(
                        resp -> resp.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()
                            == lastFilteredRequest)
                    .peek(rbelElement -> lastResponse = rbelElement)
                    .findAny()
                    .isPresent());
        } catch (ConditionTimeoutException cte) {
            log.error("Missing response message to filtered request!\n\n{}", lastFilteredRequest.getRawStringContent());
            throw new TigerLibraryException("Missing response message to filtered request!", cte);
        }
    }

    protected RbelElement findRequestByDescription(final String path, final String rbelPath, final String value,
        boolean startFromLastRequest) {
        int waitsec = (int) context.getContext("tiger").computeIfAbsent("rbel.request.timeout", key -> 5);

        Map<String, Object> threadLocalContext = getContext().getContext("tiger");

        AtomicReference<RbelElement> candidate = new AtomicReference<>();
        try {
            await("Waiting for matching request").atMost(waitsec, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .pollDelay(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Optional<RbelElement> found = filterRequests(path, rbelPath, value, startFromLastRequest,
                        threadLocalContext);
                    found.ifPresent(candidate::set);
                    return found.isPresent();
                });
        } catch (ConditionTimeoutException cte) {
            log.error("Didn't find any matching request!");
            printAllPathsOfMessages(getRbelMessages());
            if (rbelPath == null) {
                throw new AssertionError(
                    "No request with path '" + path + "' found in messages");
            } else {
                throw new AssertionError(
                    "No request with path '" + path + "' and rbelPath '" + rbelPath
                        + "' matching '" + value + "' found in messages");
            }
        }
        return candidate.get();
    }

    protected Optional<RbelElement> filterRequests(final String path, final String rbelPath, final String value,
        boolean startFromLastRequest, Map<String, Object> context) {

        List<RbelElement> msgs = getRbelMessages();
        if (startFromLastRequest) {
            final RbelElement prevRequest = getLastFilteredRequest();
            int idx = -1;
            for (var i = 0; i < msgs.size(); i++) {
                if (msgs.get(i) == prevRequest) {
                    idx = i;
                    break;
                }
            }
            msgs = new ArrayList<>(msgs.subList(idx + 2, msgs.size()));
        }

        final String hostFilter = (String) context.get("rbel.request.filter.host");
        final String methodFilter = (String) context.get("rbel.request.filter.method");

        final List<RbelElement> candidateMessages = msgs.stream()
            .filter(el -> el.hasFacet(RbelHttpRequestFacet.class))
            .filter(req -> doesPathOfMessageMatch(req, path))
            .filter(req -> hostFilter == null || hostFilter.isEmpty() || doesHostMatch(req, hostFilter))
            .filter(req -> methodFilter == null || methodFilter.isEmpty() || doesMethodMatch(req, methodFilter))
            .collect(Collectors.toList());
        if (candidateMessages.isEmpty()) {
            return Optional.empty();
        }

        if (StringUtils.isEmpty(rbelPath)) {
            if (candidateMessages.size() > 1) {
                log.warn("Found more then one candidate message. "
                    + "Returning first message. This may not be deterministic!");
                printAllPathsOfMessages(candidateMessages);
            }
            return Optional.of(candidateMessages.get(0));
        }

        for (RbelElement candidateMessage : candidateMessages) {
            final List<RbelElement> pathExecutionResult = new RbelPathExecutor(candidateMessage, rbelPath).execute();
            if (pathExecutionResult.isEmpty()) {
                continue;
            }
            if (StringUtils.isEmpty(value)) {
                return Optional.of(candidateMessage);
            } else {
                String content = pathExecutionResult.get(0).getRawStringContent();
                if (content.equals(value) ||
                    content.matches(value) ||
                    Pattern.compile(value, Pattern.DOTALL).matcher(content).matches()) {
                    return Optional.of(candidateMessage);
                } else {
                    log.info("Found rbel node but \n'" + content + "' didnt match\n'" + value + "'");
                }
            }
        }
        return Optional.empty();
    }

    public boolean doesPathOfMessageMatch(final RbelElement req, final String path) {
        try {
            URI uri = new URI(req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
                .orElse(""));
            return uri.getPath().equals(path) || uri.getPath().matches(path);
        } catch (final URISyntaxException e) {
            return false;
        } catch (PatternSyntaxException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public boolean doesHostMatch(final RbelElement req, final String hostFilter) {
        try {
            String host = req.getFacetOrFail(RbelHttpMessageFacet.class)
                .getHeader().getFacetOrFail(RbelHttpHeaderFacet.class)
                .get("Host").getRawStringContent();
            return host.equals(hostFilter) || host.matches(hostFilter);
        } catch (RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public boolean doesMethodMatch(final RbelElement req, final String method) {
        try {
            String reqMethod = req.getFacetOrFail(RbelHttpRequestFacet.class).getMethod().getRawStringContent()
                .toUpperCase();
            return method.equals(reqMethod) || method.matches(reqMethod);
        } catch (RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    private void printAllPathsOfMessages(List<RbelElement> msgs) {
        log.info("Found the following {} messages:\n{} ", msgs.size(), msgs.stream()
            .map(msg -> msg.getFacet(RbelHttpRequestFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(req -> "=>\t" + req.getPathAsString() + " : " + req.getChildElements())
            .collect(Collectors.joining("\n")));
    }

    public void compareXMLStructure(String test, String oracle, List<Function<DiffBuilder, DiffBuilder>> diffOptions) {
        ArrayList<Difference> diffs = new ArrayList<>();
        Source srcTest = Input.from(test).build();
        Source srcOracle = Input.from(oracle).build();
        DiffBuilder db = DiffBuilder.compare(srcOracle).withTest(srcTest);
        for (Function<DiffBuilder, DiffBuilder> src : diffOptions) {
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

        Diff diff = db.build();
        assertThat(diff.hasDifferences()).withFailMessage("XML tree mismatch!\n" + diff).isFalse();
    }

    public void compareXMLStructure(String test, String oracle) {
        compareXMLStructure(test, oracle, Collections.emptyList());
    }

    private static final Map<String, Function<DiffBuilder, DiffBuilder>> diffOptionMap = new HashMap<>();

    static {
        diffOptionMap.put("nocomment", DiffBuilder::ignoreComments);
        diffOptionMap.put("txtignoreempty", DiffBuilder::ignoreElementContentWhitespace);
        diffOptionMap.put("txttrim", DiffBuilder::ignoreWhitespace);
        diffOptionMap.put("txtnormalize", DiffBuilder::normalizeWhitespace);
    }

    @SneakyThrows
    public void compareXMLStructure(String test, String oracle, String diffOptionCSV) {
        final List<Function<DiffBuilder, DiffBuilder>> diffOptions = new ArrayList<>();
        Arrays.stream(diffOptionCSV.split(","))
            .map(String::trim)
            .forEach(srcClassId -> {
                assertThat(diffOptionMap).containsKey(srcClassId);
                diffOptions.add(diffOptionMap.get(srcClassId));
            });
        compareXMLStructure(test, oracle, diffOptions);
    }

    public RbelElement findElemInLastResponse(final String rbelPath) {
        try {
            List<RbelElement> elems = lastResponse.findRbelPathMembers(rbelPath);
            assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
            assertThat(elems).withFailMessage("Expected exactly one match fpr path '" + rbelPath + "'!").hasSize(1);
            return elems.get(0);
        } catch (Exception e) {
            throw new AssertionError("Unable to find element in last response for rbel path '" + rbelPath + "'");
        }
    }


}
