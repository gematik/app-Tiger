/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections.ListUtils;

@Data
@EqualsAndHashCode(callSuper = true)
public class TigerConfigurationKey extends ArrayList<TigerConfigurationKeyString> {

  private static final String ALL_SNAKE_AND_UPPERCASE_REGEX = "([A-Z0-9]+_)*[A-Z0-9]+";

  public TigerConfigurationKey(TigerConfigurationKey baseKeys) {
    super(baseKeys);
  }

  public TigerConfigurationKey(String... baseKeys) {
    super(splitKeys(baseKeys));
  }

  public TigerConfigurationKey(String[] baseKeys, String... additionalKeys) {
    super(splitKeys(baseKeys, additionalKeys));
  }

  public TigerConfigurationKey(TigerConfigurationKey baseKey, String key) {
    super(ListUtils.sum(baseKey, List.of(new TigerConfigurationKeyString(key))));
  }

  public TigerConfigurationKey(List<TigerConfigurationKeyString> list) {
    super(list);
  }

  public TigerConfigurationKey() {
    super();
  }

  private static List<TigerConfigurationKeyString> splitKeys(
      String[] baseKeys, String[] additionalKeys) {
    final List<TigerConfigurationKeyString> keys = splitKeys(baseKeys);
    keys.addAll(splitKeys(additionalKeys));
    return keys;
  }

  private static List<TigerConfigurationKeyString> splitKey(String key) {
    if (key.matches(ALL_SNAKE_AND_UPPERCASE_REGEX)) {
      return Stream.of(key.split("_"))
          .map(String::toLowerCase)
          .map(TigerConfigurationKeyString::wrapAsKey)
          .collect(Collectors.toList());
    }
    return Stream.of(key.split("\\."))
        .map(TigerConfigurationKeyString::wrapAsKey)
        .collect(Collectors.toList());
  }

  private static List<TigerConfigurationKeyString> splitKeys(String... keys) {
    return Stream.of(keys)
        .map(TigerConfigurationKey::splitKey)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  public String downsampleKey() {
    return stream().map(TigerConfigurationKeyString::asLowerCase).collect(Collectors.joining("."));
  }

  public String downsampleKeyCaseSensitive() {
    return stream().map(TigerConfigurationKeyString::asString).collect(Collectors.joining("."));
  }

  public boolean isBelow(TigerConfigurationKey reference) {
    if (reference == null || reference.size() >= size()) {
      return false;
    }
    for (int i = 0; i < reference.size(); i++) {
      if (!reference.get(i).equals(get(i))) {
        return false;
      }
    }
    return true;
  }

  public TigerConfigurationKey subtractFromBeginning(TigerConfigurationKey reference) {
    if (reference == null) {
      throw new IllegalArgumentException("Could not subtract null reference");
    }
    if (reference.size() >= size()) {
      throw new IllegalArgumentException("Could not subtract longer reference from value");
    }
    return new TigerConfigurationKey(subList(reference.size(), size()));
  }

  public boolean containsKey(String key) {
    return downsampleKey().matches(key);
  }

  public void add(String key) {
    addAll(splitKeys(key));
  }
}
