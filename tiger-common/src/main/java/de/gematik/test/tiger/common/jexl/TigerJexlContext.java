package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.config.TigerConfigurationKey;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlContext;

/**
 * Context to be used in a JEXL-evaluation. The keys are of type string, but they are canonicalized
 * in accordance to the TigerConfigurationKey. The values are objects, which makes this a superset
 * of the TigerGlobalConfiguration.
 */
@Slf4j
public class TigerJexlContext extends TreeMap<String, Object> implements JexlContext {

  public static final String CURRENT_ELEMENT_MARKER = "currentElement";
  public static final String ROOT_ELEMENT_MARKER = "rootElement";
  public static final String REMAINING_PATH_FROM_REQUEST = "remainingPathToMatch";
  private static final String RBELPATH_FALLBACK_MARKER = "rbelPathFallback";
  public static final String KEY_ELEMENT_MARKER = "key";
  private static final Set<String> DEFAULT_KEYS =
      Set.of(
          CURRENT_ELEMENT_MARKER.toLowerCase(),
          ROOT_ELEMENT_MARKER.toLowerCase(),
          REMAINING_PATH_FROM_REQUEST.toLowerCase(),
          KEY_ELEMENT_MARKER.toLowerCase());

  public TigerJexlContext(Map<String, Object> initialMap) {
    this();
    putAll(initialMap);
  }

  public TigerJexlContext() {
    // necessary to allow null keys
    super(Comparator.nullsFirst(Comparator.naturalOrder()));
  }

  /** Clones the context and returns a new copy with the current element set to the given value */
  public TigerJexlContext withCurrentElement(Object o) {
    final TigerJexlContext context = new TigerJexlContext(this);
    context.put(CURRENT_ELEMENT_MARKER, o);
    return context;
  }

  /** Clones the context and returns a new copy with the root element set to the given value */
  public TigerJexlContext withRootElement(Object o) {
    final TigerJexlContext context = new TigerJexlContext(this);
    context.put(ROOT_ELEMENT_MARKER, o);
    return context;
  }

  /** Clones the context and returns a new copy with the key element set to the given value */
  public TigerJexlContext withKey(String key) {
    final TigerJexlContext context = new TigerJexlContext(this);
    context.put(KEY_ELEMENT_MARKER, key);
    return context;
  }

  /**
   * Clones the context and returns a new copy which might ignore empty RbelPaths when a JEXL-expression
   * is evaluated. If not then a exception is thrown, otherwise an empty string is taken as the fallback.
   */
  public TigerJexlContext withShouldIgnoreEmptyRbelPaths(boolean value) {
    final TigerJexlContext context = new TigerJexlContext(this);
    context.put(RBELPATH_FALLBACK_MARKER, value);
    return context;
  }

  /** Clones the context and returns a new copy with the given key/value pair added */
  public TigerJexlContext with(String key, String value) {
    final TigerJexlContext context = new TigerJexlContext(this);
    context.put(key, value);
    return context;
  }

  @Override
  public void set(String name, Object value) {
    put(name, value);
  }

  @Override
  public Object put(String name, Object value) {
    return super.put(canonicalize(name), value);
  }

  private static String canonicalize(String name) {
    if (name == null) {
      return null;
    }
    return new TigerConfigurationKey(name).downsampleKey();
  }

  @Override
  public boolean has(String name) {
    return super.containsKey(canonicalize(name));
  }

  @Override
  public Object get(String name) {
    return super.get(canonicalize(name));
  }

  public Optional<Object> getOptional(String name) {
    return Optional.ofNullable(get(name));
  }

  public Object getCurrentElement() {
    return getOptional(CURRENT_ELEMENT_MARKER).orElseGet(() -> get(ROOT_ELEMENT_MARKER));
  }

  public Object getRootElement() {
    return getOptional(ROOT_ELEMENT_MARKER).orElseGet(() -> get(CURRENT_ELEMENT_MARKER));
  }

  public boolean shouldIgnoreEmptyRbelPaths() {
    return getOptional(RBELPATH_FALLBACK_MARKER)
      .filter(Boolean.class::isInstance)
      .map(Boolean.class::cast)
      .orElse(Boolean.FALSE);
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

  public TigerJexlContext cloneContext() {
    return new TigerJexlContext(this);
  }

  public Map<String, Object> allNonStandardValues() {
    return entrySet().stream()
        .filter(e -> !DEFAULT_KEYS.contains(e.getKey().toLowerCase()))
        .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));
  }
}
