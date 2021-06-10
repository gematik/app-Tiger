/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.SneakyThrows;

public abstract class ThreadSafeDomainContextProvider {

    protected String domain = "default";

    public abstract Map<String, Object> getContext();

    public abstract Map<String, Object> getContext(String domain);

    public String getDomain() {
        return domain;
    }

    public void setDomain(final String d) {
        assertThat(d).isNotBlank();
        domain = d;
        getContext();
    }

    public String getString(final String key) {
        final Map<String, Object> ctxt = getContext();
        assertThat(ctxt).containsKey(key);
        final Object value = ctxt.get(key);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("UnusedReturnValue")
    public String putString(final String key, final String value) {
        final Object o = getContext().put(key, value);
        return o == null ? null : o.toString();
    }

    /**
     * shallow copy !
     */
    public void copyAllToDomain(final String otherDomain) {
        getContext(otherDomain).putAll(getContext());
    }

    /**
     * shallow copy !
     */
    @SuppressWarnings("UnusedReturnValue")
    public Object copyToDomain(final String otherDomain, final String key) {
        return getContext(otherDomain).put(key, getContext().get(key));

    }

    public Map<String, Object> getObjectMapCopy(final String key) {
        assertThat(getContext().get(key)).isInstanceOf(Map.class);
        //noinspection unchecked
        return new HashMap<>((Map<String, Object>) getContext().get(key));
    }

    /**
     * first checks equals then regex match
     */
    public void assertRegexMatches(final String key, final String regex) {
        final Map<String, Object> ctxt = getContext();
        if (regex == null || "$NULL".equals(regex)) {
            assertThat(ctxt).containsKey(key);
            assertThat(ctxt.get(key)).isNull();
        } else if ("$DOESNOTEXIST".equals(regex)) {
            assertThat(ctxt).doesNotContainKey(key);
        } else {
            assertThat(ctxt).containsKey(key);
            final String value = Optional.ofNullable(ctxt.get(key))
                .map(Object::toString)
                .orElse(null);
            if (!Objects.equals(value, regex)) {
                assertThat(value).matches(regex);
            }
        }
    }

    public void remove(final String key) {
        assertThat(getContext()).containsKey(key);
        getContext().remove(key);
    }

    public void flipBit(final int bitidx, final String key) {
        assertThat(getContext()).containsKey(key);
        assertThat(getContext().get(key))
            .withFailMessage("Value for '" + key + "' in context is null!")
            .isNotNull();
        final var value = getContext().get(key).toString();
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final int idx;
        final int shift;
        if (bitidx < 0) {
            idx = bytes.length - 1 + bitidx / 8;
            shift = -bitidx % 8;
        } else {
            idx = bitidx / 8;
            shift = 8 - (bitidx % 8);
        }
        bytes[idx] ^= (byte) (0b00000001 << shift);
        final var flippedValue = new String(bytes);
        assertThat(flippedValue).isNotEqualTo(value);
        getContext().put(key, flippedValue);
    }

    protected String getId() {
        return Thread.currentThread().getId() + domain;
    }

    protected String getId(final String otherDomain) {
        return Thread.currentThread().getId() + otherDomain;
    }

    @SneakyThrows
    public void assertPropFileMatches(String propFileName) {
        InputStream in = null;
        try {
            if (propFileName.startsWith("classpath:")) {
                in = getClass().getResourceAsStream(propFileName.substring("classpath:".length()));
            } else {
                in = new FileInputStream(propFileName);
            }
            assertThat(in).withFailMessage("Unable to access properties file '" + propFileName + "'").isNotNull();
            var p = new Properties();
            p.load(in);
            getContext().keySet().forEach(key ->
                assertThat(getContext().get(key).toString()).isEqualTo(p.getProperty(key)));
        } finally {
            if (in != null) in.close();
        }
    }
}