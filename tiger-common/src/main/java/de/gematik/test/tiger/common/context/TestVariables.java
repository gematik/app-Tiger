/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.context;

import java.util.HashMap;
import java.util.Map;

public class TestVariables extends ThreadSafeDomainContextProvider {

    public TestVariables() {

    }

    public TestVariables(final String domain) {
        this.domain = domain;
    }

    private static final Map<String, Map<String, Object>> threadedContexts = new HashMap<>();

    @Override
    public Map<String, Object> getContext() {
        return threadedContexts.computeIfAbsent(getId(), threadid -> new HashMap<>());
    }

    @Override
    public Map<String, Object> getContext(final String otherDomain) {
        return threadedContexts.computeIfAbsent(getId(otherDomain), threadid -> new HashMap<>());
    }

    public String substituteVariables(final String str) {
        return substituteTokens(str, "VAR", getContext());
    }
}
