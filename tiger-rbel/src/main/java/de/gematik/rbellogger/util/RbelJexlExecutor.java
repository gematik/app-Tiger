/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import com.google.common.base.CharMatcher;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;


@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class RbelJexlExecutor extends TigerJexlExecutor {

    private static final String RBEL_PATH_CHARS = "(\\$\\.|\\w|\\.)+.*";

    static {
        TokenSubstituteHelper.REPLACER_ORDER.addFirst(
            Pair.of('?', (str, source, ctx) -> ctx
                .map(TigerJexlContext::getCurrentElement)
                .filter(Objects::nonNull)
                .filter(RbelElement.class::isInstance)
                .map(RbelElement.class::cast)
                .flatMap(el -> el.findElement(str))
                .map(el -> el.printValue()
                    .orElseGet(el::getRawStringContent)))
        );
    }

    private static final int MAXIMUM_JEXL_ELEMENT_SIZE = 16_000;

    public static boolean matchAsTextExpression(Object element, String textExpression) {
        try {
            final boolean textMatchResult = ((RbelElement) element).getRawStringContent().contains(textExpression);
            final boolean regexMatchResult = Pattern.compile(textExpression)
                .matcher(((RbelElement) element).getRawStringContent()).find();

            return textMatchResult || regexMatchResult;
        } catch (Exception e) {
            if (ACTIVATE_JEXL_DEBUGGING) {
                log.info("Error during Text search.", e);
            }
            return false;
        }
    }

    @Override
    public JexlExpression buildExpression(String jexlExpression, TigerJexlContext mapContext) {
        return super.buildExpression(evaluateRbelPathExpressions(jexlExpression, mapContext), mapContext);
    }

    private String evaluateRbelPathExpressions(String jexlExpression, TigerJexlContext mapContext) {
        for (var potentialPath : extractPotentialRbelPaths(jexlExpression)) {
            if (!(potentialPath.startsWith("$.") || potentialPath.startsWith("@."))) {
                continue;
            }
            final Optional<String> pathResult = extractPathAndConvertToString(
                potentialPath.startsWith("@.") ? mapContext.getCurrentElement() : mapContext.getRootElement(),
                potentialPath.startsWith("@.") ? potentialPath.replaceFirst("@\\.", "\\$.") : potentialPath);
            if (pathResult.isEmpty() || !CharMatcher.ascii().matchesAllOf(pathResult.get())) {
                continue;
            }
            final String id = "replacedPath_" + RandomStringUtils.randomAlphabetic(20); //NOSONAR
            mapContext.put(id, pathResult.get());
            jexlExpression = jexlExpression.replace(potentialPath, id);
        }
        return jexlExpression;
    }

    public static List<String> extractPotentialRbelPaths(String jexlExpression) {
        List<String> rbelPaths = new ArrayList<>();
        boolean insideRbelPath = false;
        boolean insideNestedJexlExpression = false;
        int jexlExpressionStart = -1;
        int pos = 0;
        IntPredicate closingJexlBracketIsNext = p -> jexlExpression.startsWith(")]", p);
        IntPredicate openingJexlBracketIsNext = p -> jexlExpression.startsWith("[?(", p);
        IntPredicate nextCharIsNotStillRbelPath = p -> !jexlExpression.substring(p).matches(RBEL_PATH_CHARS); //NOSONAR
        IntPredicate startingRbelPathIsNext = p -> jexlExpression.startsWith("$.", p) || jexlExpression.startsWith("@.", p);

        while (pos < jexlExpression.length()) {
            if (insideNestedJexlExpression) {
                if (closingJexlBracketIsNext.test(pos)) {
                    insideNestedJexlExpression = false;
                    pos++;
                }
            } else if (insideRbelPath) {
                if (openingJexlBracketIsNext.test(pos)) {
                    insideNestedJexlExpression = true;
                    pos += 2;
                } else if (nextCharIsNotStillRbelPath.test(pos)) {
                    rbelPaths.add(jexlExpression.substring(jexlExpressionStart, pos));
                    insideRbelPath = false;
                }
            } else {
                if (startingRbelPathIsNext.test(pos)) {
                    insideRbelPath = true;
                    jexlExpressionStart = pos;
                    pos++;
                }
            }
            pos++;
        }

        // End of string and rbelPath still going: Add the current rbelPath
        if (insideRbelPath) {
            rbelPaths.add(jexlExpression.substring(jexlExpressionStart, pos));
        }

        return rbelPaths;
    }

    private static Optional<String> extractPathAndConvertToString(Object source, String rbelPath) {
        return Optional.ofNullable(source)
            .filter(RbelElement.class::isInstance)
            .map(RbelElement.class::cast)
            .flatMap(s -> s.findElement(rbelPath))
            .map(RbelJexlExecutor::forceStringConvert);
    }

    @Override
    public TigerJexlContext buildJexlMapContext(Object element, Optional<String> key) {
        final TigerJexlContext mapContext = new TigerJexlContext();
        mapContext.putAll(super.buildJexlMapContext(element, key));
        final Optional<RbelElement> parentElement = getParentElement(element);

        mapContext.put("parent", parentElement.orElse(null));
        final Optional<RbelElement> message = findMessage(element);
        mapContext.put("message", message
            .map(this::convertToJexlMessage)
            .orElse(null));
        if (element instanceof RbelElement) {
            mapContext.put("charset", ((RbelElement) element).getElementCharset().displayName());
            mapContext.put("@", buildPositionDescriptor((RbelElement) element));
            mapContext.put("pos", buildPositionDescriptor((RbelElement) element));
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

        return mapContext;
    }

    @Override
    public String getContent(Object element) {
        if (element instanceof RbelElement) {
            return getMaxedOutContentOfElement((RbelElement) element);
        } else {
            return super.getContent(element);
        }
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

    private Map<String, Object> buildPositionDescriptor(RbelElement element) {
        final HashMap<String, Object> result = new HashMap<>();
        element.getChildNodesWithKey().stream()
            .forEach(entry -> {
                final String key = buildKey(entry.getKey());
                if (entry.getValue().hasFacet(RbelJsonFacet.class) && entry.getValue()
                    .hasFacet(RbelNestedFacet.class)) {
                    result.put(key,
                        getMaxedOutContentOfElement(
                            entry.getValue().getFacetOrFail(RbelNestedFacet.class).getNestedElement()));
                } else if (entry.getValue().hasFacet(RbelValueFacet.class)) {
                    final Object value = entry.getValue().getFacetOrFail(RbelValueFacet.class).getValue();
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
        } catch (RuntimeException e) {
            isPureInteger = false;
        }
        if (isPureInteger) {
            return "_" + key;
        }
        return key;
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
            .path(element.getFacet(RbelHttpRequestFacet.class)
                .map(RbelHttpRequestFacet::getPath).map(RbelElement::getRawStringContent)
                .flatMap(this::convertToUrl).map(URI::getPath)
                .orElse(null))
            .bodyAsString(bodyOptional.map(RbelElement::getRawStringContent)
                .orElse(null))
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

    private Optional<URI> convertToUrl(String rawUrl) {
        try {
            return Optional.of(new URI(rawUrl));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
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
        public final String path;
        public final String statusCode;
        public final boolean request;
        public final boolean response;
        public final Map<String, List<String>> headers;
        public final String bodyAsString;
        public final RbelElement body;
    }

    private static String forceStringConvert(RbelElement obj) {
        if (obj.getFirst("content").isPresent()) {
            return obj.getFirst("content").map(RbelJexlExecutor::forceStringConvert).orElse("");
        } else if (obj.hasFacet(RbelValueFacet.class)) {
            return obj.getFacetOrFail(RbelValueFacet.class).getValue().toString();
        } else if (obj.getRawContent() != null) {
            return obj.getRawStringContent();
        } else {
            return "";
        }
    }
}
