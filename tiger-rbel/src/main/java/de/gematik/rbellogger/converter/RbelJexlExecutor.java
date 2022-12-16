/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.RbelOptions.ACTIVATE_JEXL_DEBUGGING;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;

@Slf4j
@Data
public class RbelJexlExecutor {

    private static final Map<Integer, JexlExpression> JEXL_EXPRESSION_CACHE = new HashMap<>();
    private static final int MAXIMUM_JEXL_ELEMENT_SIZE = 16_000;

    public boolean matchesAsJexlExpression(Object element, String jexlExpression) {
        return matchesAsJexlExpression(element, jexlExpression, Optional.empty());
    }

    public boolean matchesAsJexlExpression(Object element, String jexlExpression, Optional<String> key) {
        try {
            final MapContext mapContext = new MapContext(buildJexlMapContext(element, key));
            final JexlExpression expression = buildExpression(evaluateRbelPathExpressions(jexlExpression, element, mapContext));

            final boolean result = Optional.of(expression.evaluate(mapContext))
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);

            if (result && ACTIVATE_JEXL_DEBUGGING) {
                if (element instanceof RbelElement) {
                    log.debug("Found match: '{}' with path {} matches '{}'", element,
                        ((RbelElement) element).findNodePath(), jexlExpression);
                } else {
                    log.debug("Found match: '{}' matches '{}'", element, jexlExpression);
                }
            }

            return result;
        } catch (Exception e) {
            if (ACTIVATE_JEXL_DEBUGGING) {
                log.info("Error during Jexl-Evaluation.", e);
            }
            return false;
        }
    }

    public boolean matchAsTextExpression(Object element, String textExpression) {
        try {
            final boolean textMatchResult = ((RbelElement) element).getRawStringContent().contains(textExpression);
            final boolean regexMatchResult = Pattern.compile(textExpression).matcher(((RbelElement) element).getRawStringContent()).find();

            return textMatchResult || regexMatchResult;
        } catch (Exception e) {
            if (ACTIVATE_JEXL_DEBUGGING) {
                log.info("Error during Text search.", e);
            }
            return false;
        }
    }

    private String evaluateRbelPathExpressions(String jexlExpression, Object element, MapContext mapContext) {
        if (!(element instanceof RbelElement)
            || !jexlExpression.contains("$.")) {
            return jexlExpression;
        }
        String rbelPath = Arrays.stream(jexlExpression.split(" "))
            .filter(str -> str.startsWith("$.")
                && str.length() > 2)
            .findAny().orElseThrow();
        final String rbelEvaluationResult = extractPathAndConvertToString((RbelElement) element, rbelPath);
        mapContext.set("rbelEvaluationResult", rbelEvaluationResult);
        return jexlExpression.replace(rbelPath, "rbelEvaluationResult");
    }

    private static String extractPathAndConvertToString(RbelElement source, String rbelPath) {
        final Optional<RbelElement> target = source.findElement(rbelPath);
        if (target.isPresent()) {
            if (target.get().getRawContent() != null) {
                return target.get().getRawStringContent();
            } else if (target.get().hasFacet(RbelValueFacet.class)) {
                return target.get().getFacetOrFail(RbelValueFacet.class).getValue().toString();
            }
        }
        return "";
    }

    private JexlExpression buildExpression(String jexlExpression) {
        final int hashCode = jexlExpression.hashCode();
        if (JEXL_EXPRESSION_CACHE.containsKey(hashCode)) {
            return JEXL_EXPRESSION_CACHE.get(hashCode);
        }
        final JexlExpression expression = new JexlBuilder().create().createExpression(jexlExpression);
        JEXL_EXPRESSION_CACHE.put(hashCode, expression);
        return expression;
    }

    public Map<String, Object> buildJexlMapContext(Object element, Optional<String> key) {
        final Map<String, Object> mapContext = new HashMap<>();
        final Optional<RbelElement> parentElement = getParentElement(element);

        mapContext.put("element", element);
        mapContext.put("parent", parentElement.orElse(null));
        final Optional<RbelElement> message = findMessage(element);
        mapContext.put("message", message
            .map(this::convertToJexlMessage)
            .orElse(null));
        if (element instanceof RbelElement) {
            mapContext.put("charset", ((RbelElement) element).getElementCharset().displayName());
            mapContext.put("@", buildPositionDescriptor((RbelElement) element));
        }

        final Optional<RbelElement> requestMessage = tryToFindRequestMessage(element);
        final Optional<JexlMessage> responseMessage = tryToFindResponseMessage(element)
            .map(this::convertToJexlMessage);
        if (requestMessage
            .filter(msg -> message.isPresent())
            .map(msg -> message.get() == msg)
            .orElse(false)) {
            mapContext.put("request", mapContext.get("message"));
            mapContext.put("response", responseMessage.orElse(null));
            mapContext.put("isRequest", true);
            mapContext.put("isResponse", false);
        } else {
            mapContext.put("request", requestMessage
                .map(this::convertToJexlMessage)
                .orElse(null));
            mapContext.put("response", responseMessage.orElse(null));
            mapContext.put("isRequest", false);
            mapContext.put("isResponse", true);
        }
        mapContext.put("facets", Optional.ofNullable(element)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .map(RbelElement::getFacets)
            .stream()
            .flatMap(List::stream)
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .collect(Collectors.toSet()));
        mapContext.put("key", key
            .or(() -> tryToFindKeyFromParentMap(element, parentElement))
            .orElse(null));
        mapContext.put("path", Optional.ofNullable(element)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .map(RbelElement::findNodePath)
            .orElse(null));
        mapContext.put("type", element.getClass().getSimpleName());
        mapContext.put("content", getContent(element));

        return mapContext;
    }

    private static String getContent(Object element) {
        if (element instanceof RbelElement) {
            return getMaxedOutContentOfElement((RbelElement) element);
        } else {
            return element.toString();
        }
    }

    private static String getMaxedOutContentOfElement(RbelElement element) {
        if (element.getSize() < MAXIMUM_JEXL_ELEMENT_SIZE) {
            return element.getRawStringContent();
        } else {
            return "";
        }
    }

    private Map<String, String> buildPositionDescriptor(RbelElement element) {
        final HashMap<String, String> result = new HashMap<>();
        element.getChildNodesWithKey().stream()
            .forEach(entry -> {
                if (entry.getValue().hasFacet(RbelJsonFacet.class) && entry.getValue().hasFacet(RbelNestedFacet.class)) {
                    result.put(entry.getKey(),
                        getMaxedOutContentOfElement(
                            entry.getValue().getFacetOrFail(RbelNestedFacet.class).getNestedElement()));
                } else {
                    result.put(entry.getKey(), getMaxedOutContentOfElement(entry.getValue()));
                }
            });
        return result;
    }

    private Optional<RbelElement> tryToFindRequestMessage(Object element) {
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

    private Optional<RbelElement> tryToFindResponseMessage(Object element) {
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

    private JexlMessage convertToJexlMessage(RbelElement element) {
        final Optional<RbelElement> bodyOptional = element.getFirst("body")
            .filter(el -> el.getSize() < MAXIMUM_JEXL_ELEMENT_SIZE);
        return JexlMessage.builder()
            .request(element.getFacet(RbelHttpRequestFacet.class).isPresent())
            .response(element.getFacet(RbelHttpResponseFacet.class).isPresent())
            .method(element.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getMethod).map(RbelElement::getRawStringContent)
                .orElse(null))
            .url(element.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath).map(RbelElement::getRawStringContent)
                .orElse(null))
            .bodyAsString(bodyOptional.map(RbelElement::getRawStringContent).orElse(null))
            .body(bodyOptional.orElse(null))
            .statusCode(element.getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getResponseCode)
                .map(RbelElement::getRawStringContent)
                .orElse(null))
            .headers(element.getFacet(RbelHttpMessageFacet.class)
                .map(RbelHttpMessageFacet::getHeader)
                .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
                .filter(RbelHttpHeaderFacet.class::isInstance)
                .map(RbelHttpHeaderFacet.class::cast)
                .map(RbelHttpHeaderFacet::entries)
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                    Collectors.mapping(e -> e.getValue().getRawStringContent(), Collectors.toList()))))
            .build();
    }

    private Optional<RbelElement> findMessage(Object element) {
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
        if (ptr.hasFacet(RbelHttpMessageFacet.class)
            && ptr.getParentNode() == null) {
            return Optional.of(ptr);
        } else {
            return Optional.empty();
        }
    }

    private Optional<RbelElement> getParentElement(Object element) {
        return Optional.ofNullable(element)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .map(RbelElement::getParentNode);
    }

    private Optional<String> tryToFindKeyFromParentMap(Object element, Optional<RbelElement> parent) {
        return parent
            .stream()
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
        public final String statusCode;
        public final boolean request;
        public final boolean response;
        public final Map<String, List<String>> headers;
        public final String bodyAsString;
        public final RbelElement body;
    }
}
