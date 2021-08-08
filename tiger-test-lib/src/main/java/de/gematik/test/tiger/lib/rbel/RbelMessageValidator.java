/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.hooks.Hooks;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Slf4j
public class RbelMessageValidator {

    @Getter
    private RbelElement lastFilteredRequest;
    @Getter
    private RbelElement lastResponse;

    public RbelMessageValidator() {

    }

    public List<RbelElement> getRbelMessages() {
        return Hooks.getValidatableRbelMessages();
    }

    public RbelElement getResponseOfRequest(final RbelElement request) {
        return getRbelMessages().stream()
            .filter(el -> el.hasFacet(RbelHttpResponseFacet.class))
            .filter(res -> res
                .getFacetOrFail(RbelHttpResponseFacet.class)
                .getRequest() == request)
            .findAny()
            .orElseThrow(() -> new AssertionError("No response found for given request"));
    }

    public boolean doesPathOfMessageMatch(final RbelElement req, final String path) {
        try {
            return new URI(req.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
                .orElse(""))
                .getPath().equals(path);
        } catch (final URISyntaxException e) {
            return false;
        }
    }

    public void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value) {
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, getRbelMessages());
    }

    public void filterGivenRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
                                                     final List<RbelElement> msgs) {

        final RbelElement messageByDescription = findRequestByDescription(path, rbelPath, value, msgs);
        lastFilteredRequest = messageByDescription;
        lastResponse = getResponseOfRequest(messageByDescription);
    }

    private RbelElement findRequestByDescription(final String path, final String rbelPath, final String value,
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
                log.warn("Found more then one candidate message. Returning first message. This may not be deterministic!");
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
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, new ArrayList<>(msgs.subList(idx + 2, msgs.size())));
    }
}
