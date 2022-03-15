/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.google.common.collect.ImmutableList;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.hooks.TigerTestHooks;
import de.gematik.test.tiger.lib.TigerLibraryException;
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

    private static final Map<String, Function<DiffBuilder, DiffBuilder>> diffOptionMap = new HashMap<>();

    static {
        diffOptionMap.put("nocomment", DiffBuilder::ignoreComments);
        diffOptionMap.put("txtignoreempty", DiffBuilder::ignoreElementContentWhitespace);
        diffOptionMap.put("txttrim", DiffBuilder::ignoreWhitespace);
        diffOptionMap.put("txtnormalize", DiffBuilder::normalizeWhitespace);
    }

    private static final List<String> emptyPath = ImmutableList.of("", "/");

    @Getter
    protected RbelElement lastFilteredRequest;
    @Getter
    protected RbelElement lastResponse;

    public RbelMessageValidator() {

    }

    public List<RbelElement> getRbelMessages() {
        return TigerTestHooks.getValidatableRbelMessages();
    }

    public void clearRBelMessages() {
        TigerTestHooks.getValidatableRbelMessages().clear();
    }

    public void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
        final boolean startFromLastRequest) {
        final int waitsec = TigerGlobalConfiguration.readIntegerOptional("tiger.rbel.request.timeout").orElse(5);
        lastFilteredRequest = findRequestByDescription(path, rbelPath, value, startFromLastRequest);
        try {
            await("Waiting for matching response").atMost(waitsec, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> getRbelMessages().stream()
                    .filter(e -> e.hasFacet(RbelHttpResponseFacet.class))
                    .filter(
                        resp -> resp.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()
                            == lastFilteredRequest)
                    .peek(rbelElement -> lastResponse = rbelElement)
                    .findAny()
                    .isPresent());
        } catch (final ConditionTimeoutException cte) {
            log.error("Missing response message to filtered request!\n\n{}", lastFilteredRequest.getRawStringContent());
            throw new TigerLibraryException("Missing response message to filtered request!", cte);
        }
    }

    protected RbelElement findRequestByDescription(final String path, final String rbelPath, final String value,
        final boolean startFromLastRequest) {
        final int waitsec = TigerGlobalConfiguration.readIntegerOptional("tiger.rbel.request.timeout").orElse(5);

        final AtomicReference<RbelElement> candidate = new AtomicReference<>();
        try {
            await("Waiting for matching request").atMost(waitsec, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    final Optional<RbelElement> found = filterRequests(path, rbelPath, value, startFromLastRequest);
                    found.ifPresent(candidate::set);
                    return found.isPresent();
                });
        } catch (final ConditionTimeoutException cte) {
            log.error("Didn't find any matching request!");
            printAllPathsOfMessages(getRbelMessages());
            if (rbelPath == null) {
                throw new AssertionError(
                    "No request with path '" + path + "' found in messages");
            } else {
                throw new AssertionError(
                    "No request with path '" + path + "' and rbelPath '" + rbelPath
                        + "' matching '" + StringUtils.abbreviate(value, 300) + "' found in messages");
            }
        }
        return candidate.get();
    }

    protected Optional<RbelElement> filterRequests(final String path, final String rbelPath, final String value,
        final boolean startFromLastRequest) {

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

        final String hostFilter = TigerGlobalConfiguration.readString("tiger.rbel.request.filter.host", "");
        final String methodFilter = TigerGlobalConfiguration.readString("tiger.rbel.request.filter.method", "");

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

        for (final RbelElement candidateMessage : candidateMessages) {
            final List<RbelElement> pathExecutionResult = new RbelPathExecutor(candidateMessage, rbelPath).execute();
            if (pathExecutionResult.isEmpty()) {
                continue;
            }
            if (StringUtils.isEmpty(value)) {
                return Optional.of(candidateMessage);
            } else {
                final String content = pathExecutionResult.stream()
                    .map(RbelElement::getRawStringContent)
                    .map(String::trim)
                    .collect(Collectors.joining());
                try {
                    if (content.equals(value) ||
                        content.matches(value) ||
                        Pattern.compile(value, Pattern.DOTALL).matcher(content).matches()) {
                        return Optional.of(candidateMessage);
                    } else {
                        log.info("Found rbel node but \n'" + StringUtils.abbreviate(content, 300) + "' didnt match\n'"
                            + StringUtils.abbreviate(value, 300) + "'");
                    }
                } catch (final Exception ex) {
                    log.error("Failure while trying to apply regular expression '" + value + "'!", ex);
                }
            }
        }
        return Optional.empty();
    }

    public boolean doesPathOfMessageMatch(final RbelElement req, final String path) {
        try {
            final URI uri = new URI(req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
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

    public boolean doesHostMatch(final RbelElement req, final String hostFilter) {
        try {
            final String host = req.getFacetOrFail(RbelHttpMessageFacet.class)
                .getHeader().getFacetOrFail(RbelHttpHeaderFacet.class)
                .get("Host").getRawStringContent();
            return host.equals(hostFilter) || host.matches(hostFilter);
        } catch (final RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public boolean doesMethodMatch(final RbelElement req, final String method) {
        try {
            final String reqMethod = req.getFacetOrFail(RbelHttpRequestFacet.class).getMethod().getRawStringContent()
                .toUpperCase();
            return method.equals(reqMethod) || method.matches(reqMethod);
        } catch (final RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    private void printAllPathsOfMessages(final List<RbelElement> msgs) {
        log.info("Found the following {} messages:\n{} ", msgs.size(), msgs.stream()
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

    public RbelElement findElemInLastResponse(final String rbelPath) {
        try {
            final List<RbelElement> elems = lastResponse.findRbelPathMembers(rbelPath);
            assertThat(elems).withFailMessage("No node matching path '" + rbelPath + "'!").isNotEmpty();
            assertThat(elems).withFailMessage("Expected exactly one match fpr path '" + rbelPath + "'!").hasSize(1);
            return elems.get(0);
        } catch (final Exception e) {
            throw new AssertionError("Unable to find element in last response for rbel path '" + rbelPath + "'");
        }
    }

    public List<RbelElement> findElemsInLastResponse(final String rbelPath) {
        try {
            final List<RbelElement> elems = lastResponse.findRbelPathMembers(rbelPath);
            assertThat(elems).isNotEmpty();
            return elems;
        } catch (final Exception e) {
            throw new AssertionError("Unable to find element in last response for rbel path '" + rbelPath + "'");
        }
    }
}
