/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class RbelHttpHeaderFacet implements RbelFacet, Map<String, RbelElement> {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelHttpHeaderFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return table()
                .withClass("table")
                .with(
                    tbody()
                        .with(
                            element
                                .getFacetOrFail(RbelHttpHeaderFacet.class)
                                .getChildElements()
                                .stream()
                                .map(
                                    entry ->
                                        tr(
                                            td(pre(entry.getKey())),
                                            td(pre()
                                                    .with(
                                                        renderingToolkit.convert(
                                                            entry.getValue(),
                                                            Optional.ofNullable(entry.getKey())))
                                                    .withClass("value"))
                                                .with(
                                                    RbelHtmlRenderingToolkit.addNotes(
                                                        entry.getValue()))))
                                .toList()));
          }
        });
  }

  private final RbelMultiMap<RbelElement> values;

  public RbelHttpHeaderFacet() {
    this.values = new RbelMultiMap<>();
  }

  public RbelHttpHeaderFacet(RbelMultiMap<RbelElement> values) {
    this.values = values;
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public boolean isEmpty() {
    return values.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return values.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return values.containsValue(value);
  }

  @Override
  public RbelElement get(Object key) {
    return values.get(key);
  }

  @Override
  public RbelElement put(String key, RbelElement value) {
    values.put(key, value);
    return value;
  }

  @Override
  public RbelElement remove(Object key) {
    return values.remove(key);
  }

  @Override
  public void putAll(Map<? extends String, ? extends RbelElement> m) {
    values.putAll(m);
  }

  @Override
  public void clear() {
    values.clear();
  }

  @Override
  public Set<String> keySet() {
    return values.keySet();
  }

  @Override
  public List<RbelElement> values() {
    return values.stream().map(Entry::getValue).toList();
  }

  /**
   * not supported: will lose order. Use .entries() instead
   *
   * @deprecated
   */
  @Override
  @Deprecated(forRemoval = true)
  public Set<Entry<String, RbelElement>> entrySet() {
    throw new UnsupportedOperationException(
        "This method is not supported as it would not respect the order of the entries");
  }

  public List<Entry<String, RbelElement>> entries() {
    return values.getValues();
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return values;
  }

  public Stream<RbelElement> getCaseInsensitiveMatches(String key) {
    final String lowerCaseKey = key.toLowerCase();
    return values.getValues().stream()
        .filter(entry -> entry.getKey() != null)
        .filter(entry -> entry.getKey().toLowerCase().equals(lowerCaseKey))
        .map(Entry::getValue);
  }

  public boolean hasValueMatching(String headerKey, String prefix) {
    return values.getValues().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(headerKey))
        .map(Entry::getValue)
        .map(RbelElement::getRawStringContent)
        .anyMatch(str -> str.startsWith(prefix));
  }
}
