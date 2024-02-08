/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelContextDecorator {

  private static final int MAXIMUM_JEXL_ELEMENT_SIZE = 16_000;
  public static final String CONTENT = "content";

  public static void buildJexlMapContext(
      Object element, Optional<String> key, TigerJexlContext mapContext) {
    final Optional<RbelElement> parentElement = getParentElement(element);

    mapContext.put("parent", parentElement.orElse(null));
    final Optional<RbelElement> message = findMessage(element);
    mapContext.put("message", message.map(RbelContextDecorator::convertToJexlMessage).orElse(null));
    if (element instanceof RbelElement rbelElement) {
      mapContext.put(CONTENT, getMaxedOutContentOfElement(rbelElement));
      mapContext.put("charset", rbelElement.getElementCharset().displayName());
      mapContext.put("@", buildPositionDescriptor(rbelElement));
      mapContext.put("pos", buildPositionDescriptor(rbelElement));
    }

    final Optional<RbelElement> requestMessage = tryToFindRequestMessage(element);
    final Optional<JexlMessage> responseMessage =
        tryToFindResponseMessage(element).map(RbelContextDecorator::convertToJexlMessage);
    if (requestMessage
        .filter(msg -> message.isPresent())
        .map(msg -> message.get() == msg)
        .orElse(false)) {
      mapContext.put("request", mapContext.get("message"));
      mapContext.put("response", responseMessage.orElse(null));
      mapContext.put("isRequest", true);
      mapContext.put("isResponse", false);
    } else {
      mapContext.put(
          "request", requestMessage.map(RbelContextDecorator::convertToJexlMessage).orElse(null));
      mapContext.put("response", responseMessage.orElse(null));
      mapContext.put("isRequest", false);
      mapContext.put("isResponse", true);
    }
    mapContext.put(
        "facets",
        Optional.ofNullable(element)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .map(RbelElement::getFacets)
            .stream()
            .flatMap(Queue::stream)
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .collect(Collectors.toSet()));
    mapContext.put(
        "key", key.or(() -> tryToFindKeyFromParentMap(element, parentElement)).orElse(null));
    mapContext.put(
        "path",
        Optional.ofNullable(element)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .map(RbelElement::findNodePath)
            .orElse(null));
  }

  private static String getMaxedOutContentOfElement(RbelElement element) {
    if (element.getSize() < MAXIMUM_JEXL_ELEMENT_SIZE) {
      return element.getRawStringContent();
    } else {
      return "";
    }
  }

  private static String getMaxedOutContentOfElement(String string) {
    return StringUtils.abbreviate(string, MAXIMUM_JEXL_ELEMENT_SIZE);
  }

  private static Map<String, Object> buildPositionDescriptor(RbelElement element) {
    final HashMap<String, Object> result = new HashMap<>();
    element.getChildNodesWithKey().stream()
        .forEach(
            entry -> {
              final String key = buildKey(entry.getKey());
              if (entry.getValue().hasFacet(RbelJsonFacet.class)
                  && entry.getValue().hasFacet(RbelNestedFacet.class)) {
                result.put(
                    key,
                    getMaxedOutContentOfElement(
                        entry.getValue().getFacetOrFail(RbelNestedFacet.class).getNestedElement()));
              } else if (entry.getValue().hasFacet(RbelValueFacet.class)) {
                final Object value =
                    entry.getValue().getFacetOrFail(RbelValueFacet.class).getValue();
                if (value != null) {
                  result.put(key, getMaxedOutContentOfElement(value.toString()));
                } else {
                  result.put(key, null);
                }
              } else if (!entry.getValue().getChildNodes().isEmpty()) {
                result.put(key, buildPositionDescriptor(entry.getValue()));
              } else {
                result.put(key, getMaxedOutContentOfElement(entry.getValue()));
              }
            });
    return result;
  }

  private static String buildKey(String key) {
    boolean isPureInteger = true;
    try {
      Integer.parseInt(key);
    } catch (NumberFormatException e) {
      isPureInteger = false;
    }
    if (isPureInteger) {
      return "_" + key;
    }
    return key;
  }

  private static Optional<RbelElement> tryToFindRequestMessage(Object element) {
    if (!(element instanceof RbelElement)) {
      return Optional.empty();
    }
    final Optional<RbelElement> message = findMessage(element);
    if (message.isEmpty()) {
      return Optional.empty();
    }
    if (message.get().getFacet(RbelHttpRequestFacet.class).isPresent()) {
      return message;
    } else {
      return message
          .flatMap(el -> el.getFacet(RbelHttpResponseFacet.class))
          .map(RbelHttpResponseFacet::getRequest);
    }
  }

  private static Optional<RbelElement> tryToFindResponseMessage(Object element) {
    if (!(element instanceof RbelElement)) {
      return Optional.empty();
    }
    final Optional<RbelElement> message = findMessage(element);
    if (message.isEmpty()) {
      return Optional.empty();
    }
    if (message.get().getFacet(RbelHttpResponseFacet.class).isPresent()) {
      return message;
    } else {
      return message
          .flatMap(msg -> msg.getFacet(RbelHttpRequestFacet.class))
          .map(RbelHttpRequestFacet::getResponse)
          .filter(Objects::nonNull);
    }
  }

  private static JexlMessage convertToJexlMessage(RbelElement element) {
    final Optional<RbelElement> bodyOptional =
        element.getFirst("body").filter(el -> el.getSize() < MAXIMUM_JEXL_ELEMENT_SIZE);
    return JexlMessage.builder()
        .request(element.getFacet(RbelHttpRequestFacet.class).isPresent())
        .response(element.getFacet(RbelHttpResponseFacet.class).isPresent())
        .method(
            element
                .getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getMethod)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
        .url(
            element
                .getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
        .path(
            element
                .getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
                .flatMap(RbelContextDecorator::convertToUrlSafe)
                .map(URI::getPath)
                .orElse(null))
        .bodyAsString(bodyOptional.map(RbelElement::getRawStringContent).orElse(null))
        .body(bodyOptional.orElse(null))
        .statusCode(
            element
                .getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getResponseCode)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
        .headers(
            element
                .getFacet(RbelHttpMessageFacet.class)
                .map(RbelHttpMessageFacet::getHeader)
                .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
                .filter(RbelHttpHeaderFacet.class::isInstance)
                .map(RbelHttpHeaderFacet.class::cast)
                .map(RbelHttpHeaderFacet::entries)
                .stream()
                .flatMap(List::stream)
                .collect(
                    Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(
                            e -> e.getValue().getRawStringContent(), Collectors.toList()))))
        .build();
  }

  private static Optional<URI> convertToUrlSafe(String rawUrl) {
    try {
      return Optional.of(new URI(rawUrl));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static Optional<RbelElement> findMessage(Object element) {
    if (!(element instanceof RbelElement)) {
      return Optional.empty();
    }
    RbelElement ptr = (RbelElement) element;
    while (ptr.getParentNode() != null) {
      if (ptr.getParentNode() == ptr) {
        break;
      }
      ptr = ptr.getParentNode();
    }
    if (ptr.hasFacet(RbelHttpMessageFacet.class) && ptr.getParentNode() == null) {
      return Optional.of(ptr);
    } else {
      return Optional.empty();
    }
  }

  private static Optional<RbelElement> getParentElement(Object element) {
    return Optional.ofNullable(element)
        .filter(RbelElement.class::isInstance)
        .map(RbelElement.class::cast)
        .map(RbelElement::getParentNode);
  }

  private static Optional<String> tryToFindKeyFromParentMap(
      Object element, Optional<RbelElement> parent) {
    return parent.stream()
        .map(RbelElement::getChildNodesWithKey)
        .flatMap(RbelMultiMap::stream)
        .filter(entry -> entry.getValue() == element)
        .map(Map.Entry::getKey)
        .findFirst();
  }

  @Builder
  @Data
  public static class JexlMessage {

    public final String method;
    public final String url;
    public final String path;
    public final String statusCode;
    public final boolean request;
    public final boolean response;
    public final Map<String, List<String>> headers;
    public final String bodyAsString;
    public final RbelElement body;
  }

  public static String forceStringConvert(RbelPathAble obj) {
    if (obj.getFirst(CONTENT).isPresent()) {
      return obj.getFirst(CONTENT).map(RbelContextDecorator::forceStringConvert).orElse("");
    } else if (obj instanceof RbelElement rbelElement
        && rbelElement.hasFacet(RbelValueFacet.class)) {
      return rbelElement.getFacetOrFail(RbelValueFacet.class).getValue().toString();
    } else if (obj.getRawStringContent() != null) {
      return obj.getRawStringContent();
    } else {
      return "";
    }
  }
}
