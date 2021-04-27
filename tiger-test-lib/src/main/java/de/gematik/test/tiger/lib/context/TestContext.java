package de.gematik.test.tiger.lib.context;

import java.util.HashMap;
import java.util.Map;

public class TestContext extends ThreadSafeDomainContextProvider {

    public TestContext() {

    }

    public TestContext(final String domain) {
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
}
