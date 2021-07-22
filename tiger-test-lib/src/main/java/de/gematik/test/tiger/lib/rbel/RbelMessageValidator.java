/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.common.context.TestContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class RbelMessageValidator {

    private final TestContext testContext;
    private final String rbelMsgsContextKey;
    private final String lastRbelRequestContextKey;

    public RbelMessageValidator() {
        this("default", "rbelmsgs", "rbelRequest");
    }

    public RbelMessageValidator(final String testContextDomain) {
        this(testContextDomain, "rbelmsgs", "rbelRequest");
    }

    public RbelMessageValidator(final String testContextDomain, final String rbelMsgsContextKey,
                                final String lastRbelRequestContextKey) {
        testContext = new TestContext(testContextDomain);
        this.rbelMsgsContextKey = rbelMsgsContextKey;
        this.lastRbelRequestContextKey = lastRbelRequestContextKey;
    }

    public List<RbelElement> getRbelMessages() {
        return ((List<RbelElement>) testContext.getContext().get(rbelMsgsContextKey));
    }

    public RbelElement getLastRequest() {
        return (RbelElement) testContext.getContext().get(lastRbelRequestContextKey);
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

    public boolean getPath(final RbelElement req, final String path) {
        try {
            return new URL(req.getFacet(RbelHttpRequestFacet.class)
                    .map(RbelHttpRequestFacet::getPath)
                    .map(RbelElement::getRawStringContent)
                    .orElse(""))
                    .getPath().equals(path);
        } catch (final MalformedURLException e) {
            return false;
        }
    }

    public void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value) {
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, getRbelMessages());
    }

    public void filterGivenRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
                                              final List<RbelElement> msgs) {
        final RbelElement request = msgs.stream()
                .filter(el -> el.hasFacet(RbelHttpRequestFacet.class))
                .filter(req -> getPath(req, path))
                .filter(req ->
                        value == null || rbelPath == null ||
                                (!new RbelPathExecutor(req, rbelPath).execute().isEmpty() &&
                                        (new RbelPathExecutor(req, rbelPath).execute().get(0).getRawStringContent().equals(value) ||
                                                new RbelPathExecutor(req, rbelPath).execute().get(0).getRawStringContent().matches(value))))
                .findAny()
                .orElseThrow(() ->
                        new AssertionError(
                                "No request with path '" + path + "' and rbelPath '" + rbelPath + "' matching '" + value
                                        + "'"));
        testContext.getContext().put("rbelRequest", request);
        testContext.getContext().put("rbelResponse", getResponseOfRequest(request));
    }

    public void filterNextRequestAndStoreInContext(final String path, final String rbelPath, final String value) {
        List<RbelElement> msgs = getRbelMessages();
        final RbelElement prevRequest = getLastRequest();
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
