/*
 * Copyright (c) 2021 gematik GmbH
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

import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.rbellogger.data.elements.RbelHttpRequest;
import de.gematik.rbellogger.data.elements.RbelHttpResponse;
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

    public List<RbelMessage> getRbelMessages() {
        return ((List<RbelMessage>) testContext.getContext().get(rbelMsgsContextKey));
    }

    public RbelHttpRequest getLastRequest() {
        return (RbelHttpRequest) testContext.getContext().get(lastRbelRequestContextKey);
    }


    RbelHttpResponse getResponseOfRequest(final RbelHttpRequest request) {
        return getRbelMessages().stream()
            .map(RbelMessage::getHttpMessage)
            .filter(RbelHttpResponse.class::isInstance)
            .map(RbelHttpResponse.class::cast)
            .filter(res -> res.getRequest() == request)
            .findAny()
            .orElseThrow(() -> new AssertionError("No response found for given request"));
    }

    boolean getPath(final RbelHttpRequest req, final String path) {
        try {
            return new URL(req.getPath().getBasicPath().getContent()).getPath().equals(path);
        } catch (final MalformedURLException e) {
            return false;
        }
    }

    void filterRequestsAndStoreInContext(final String path, final String rbelPath, final String value) {
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, getRbelMessages());
    }

    void filterGivenRequestsAndStoreInContext(final String path, final String rbelPath, final String value,
        final List<RbelMessage> msgs) {
        final RbelHttpRequest request = msgs.stream()
            .map(RbelMessage::getHttpMessage)
            .filter(RbelHttpRequest.class::isInstance)
            .map(RbelHttpRequest.class::cast)
            .filter(req -> getPath(req, path))
            .filter(req ->
                value == null || rbelPath == null ||
                    (!new RbelPathExecutor(req, rbelPath).execute().isEmpty() &&
                        (new RbelPathExecutor(req, rbelPath).execute().get(0).getContent().equals(value) ||
                            new RbelPathExecutor(req, rbelPath).execute().get(0).getContent().matches(value))))
            .findAny()
            .orElseThrow(() ->
                new AssertionError(
                    "No request with path '" + path + "' and rbelPath '" + rbelPath + "' matching '" + value
                        + "'"));
        testContext.getContext().put("rbelRequest", request);
        testContext.getContext().put("rbelResponse", getResponseOfRequest(request));
    }

    void filterNextRequestAndStoreInContext(final String path, final String rbelPath, final String value) {
        List<RbelMessage> msgs = getRbelMessages();
        final RbelHttpRequest prevRequest = getLastRequest();
        int idx = -1;
        for (var i = 0; i < msgs.size(); i++) {
            if (msgs.get(i).getHttpMessage() == prevRequest) {
                idx = i;
                break;
            }
        }
        filterGivenRequestsAndStoreInContext(path, rbelPath, value, new ArrayList<>(msgs.subList(idx + 2, msgs.size())));
    }
}
