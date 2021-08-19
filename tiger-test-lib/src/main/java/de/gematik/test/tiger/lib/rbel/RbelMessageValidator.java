/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.hooks.TigerTestHooks;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.transform.Source;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

@SuppressWarnings("unused")
@Slf4j
public class RbelMessageValidator {

    @Getter
    protected RbelElement lastFilteredRequest;
    @Getter
    protected RbelElement lastResponse;

    public RbelMessageValidator() {

    }

    public List<RbelElement> getRbelMessages() {
        return TigerTestHooks.getValidatableRbelMessages();
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
        } catch (RuntimeException rte) {
            log.error("Probable error while parsing regex!", rte);
            return false;
        }
    }

    public void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value) {
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, getRbelMessages());
    }

    public void filterGivenRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
        final List<RbelElement> msgs) {

        lastFilteredRequest = findRequestByDescription(path, rbelPath, value, msgs);
        lastResponse = msgs.stream()
            .filter(e -> e.hasFacet(RbelHttpResponseFacet.class))
            .filter(resp -> resp.getFacetOrFail(RbelHttpResponseFacet.class).getRequest() == lastFilteredRequest)
            .findAny()
            .orElseThrow();
    }

    protected RbelElement findRequestByDescription(final String path, final String rbelPath, final String value,
        final List<RbelElement> msgs) {
        final List<RbelElement> candidateMessages = msgs.stream()
            .filter(el -> el.hasFacet(RbelHttpRequestFacet.class))
            .filter(req -> doesPathOfMessageMatch(req, path))
            .collect(Collectors.toList());
        if (candidateMessages.isEmpty()) {
            printAllPathsOfMessages(msgs);
            if (rbelPath == null) {
                throw new AssertionError(
                    "No request with path '" + path + "' found in messages");
            } else {
                throw new AssertionError(
                    "No request with path '" + path + "' and rbelPath '" + rbelPath
                        + "' matching '" + value + "' found in messages");
            }
        }

        if (StringUtils.isEmpty(rbelPath)) {
            if (candidateMessages.size() > 1) {
                log.warn(
                    "Found more then one candidate message. Returning first message. This may not be deterministic!");
            }
            return candidateMessages.get(0);
        }

        for (RbelElement candidateMessage : candidateMessages) {
            final List<RbelElement> pathExecutionResult = new RbelPathExecutor(candidateMessage, rbelPath).execute();
            if (pathExecutionResult.isEmpty()) {
                continue;
            }
            if (StringUtils.isEmpty(value)) {
                return candidateMessage;
            } else {
                if (!pathExecutionResult.isEmpty()
                    && (pathExecutionResult.get(0).getRawStringContent().equals(value) ||
                    pathExecutionResult.get(0).getRawStringContent().matches(value))) {
                    return candidateMessage;
                }
            }
        }

        throw new AssertionError(
            "No request with path '" + path + "' and rbelPath '" + rbelPath
                + "' matching '" + value + "' found in messages");
    }

    private void printAllPathsOfMessages(List<RbelElement> msgs) {
        log.info("Found the following messages: " + msgs.stream()
            .map(msg -> msg.getFacet(RbelHttpRequestFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(RbelHttpRequestFacet::getPath)
            .map(RbelElement::getRawStringContent)
            .map(path -> "=>\t" + path)
            .collect(Collectors.joining("\n")));
    }

    public void filterNextRequestAndStoreInContext(final String path, final String rbelPath, final String value) {
        List<RbelElement> msgs = getRbelMessages();
        final RbelElement prevRequest = getLastFilteredRequest();
        int idx = -1;
        for (var i = 0; i < msgs.size(); i++) {
            if (msgs.get(i) == prevRequest) {
                idx = i;
                break;
            }
        }
        filterGivenRequestsAndStoreInContext(path, rbelPath, value,
            new ArrayList<>(msgs.subList(idx + 2, msgs.size())));
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
