package de.gematik.test.tiger.common.jexl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlContext;

@Slf4j
public class TigerJexlContext extends HashMap<String, Object> implements JexlContext {

    public static final String CURRENT_ELEMENT_MARKER = "currentElement";
    public static final String ROOT_ELEMENT_MARKER = "rootElement";
    public static final String KEY_ELEMENT_MARKER = "key";

    public TigerJexlContext(Map<String, Object> initialMap) {
        super(initialMap);
    }

    public TigerJexlContext() {
        super();
    }

    public TigerJexlContext withCurrentElement(Object o) {
        put(CURRENT_ELEMENT_MARKER, o);
        return this;
    }

    public TigerJexlContext withRootElement(Object o) {
        put(ROOT_ELEMENT_MARKER, o);
        return this;
    }

    public TigerJexlContext withKey(String key) {
        put(KEY_ELEMENT_MARKER, key);
        return this;
    }

    @Override
    public Object get(String name) {
        return super.get(name);
    }

    @Override
    public void set(String name, Object value) {
        super.put(name, value);
    }

    @Override
    public boolean has(String name) {
        return super.containsKey(name);
    }

    public Object getCurrentElement() {
        return Optional.ofNullable(get(CURRENT_ELEMENT_MARKER))
            .orElseGet(() -> get(ROOT_ELEMENT_MARKER));
    }

    public Object getRootElement() {
        return Optional.ofNullable(get(ROOT_ELEMENT_MARKER))
            .orElseGet(() -> get(CURRENT_ELEMENT_MARKER));
    }

    public String getKey() {
        if (super.containsKey(KEY_ELEMENT_MARKER)) {
            final Object result = super.get(KEY_ELEMENT_MARKER);
            if (result != null) {
                return result.toString();
            }
        }
        return null;
    }
}
