/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;

@Slf4j
public class TigerJexlExecutor {

    private static final Map<Integer, JexlExpression> JEXL_EXPRESSION_CACHE = new HashMap<>();
    public static final Deque<Object> ELEMENT_STACK = new ConcurrentLinkedDeque<>();
    public static boolean ACTIVATE_JEXL_DEBUGGING = false;
    public static TigerJexlExecutor INSTANCE = new TigerJexlExecutor();

    private static final Map<String, Object> NAMESPACE_MAP = new HashMap<>();

    static {
        NAMESPACE_MAP.put(null, InlineJexlToolbox.class);
    }

    public boolean matchesAsJexlExpression(Object element, String jexlExpression) {
        return matchesAsJexlExpression(element, jexlExpression, Optional.empty());
    }

    public boolean matchesAsJexlExpression(Object element, String jexlExpression, Optional<String> key) {
        final boolean result = evaluateJexlExpression(element, jexlExpression, key)
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .orElse(false);

        if (result && ACTIVATE_JEXL_DEBUGGING) {
            printDebugMessage(element, jexlExpression);
        }

        return result;
    }

    public Optional<Object> evaluateJexlExpression(String jexlExpression, Optional<String> key) {
        return evaluateJexlExpression(ELEMENT_STACK.peekFirst(), jexlExpression, key);
    }

    public Optional<Object> evaluateJexlExpression(Object element, String jexlExpression, Optional<String> key) {
        try {
            final MapContext mapContext = new MapContext(buildJexlMapContext(element, key));
            NAMESPACE_MAP.forEach(mapContext::set);
            final JexlExpression expression = buildExpression(jexlExpression, element, mapContext);

            return Optional.ofNullable(expression.evaluate(mapContext));
        } catch (RuntimeException e) {
            if (e instanceof JexlException && (e.getCause() instanceof JexlArithmetic.NullOperand)) {
                return Optional.empty();
            }
            if (e instanceof JexlException && !(e.getCause() instanceof NoSuchElementException)) {
                throw e;
            }
            log.warn("Error during Jexl-Evaluation.", e);
            return Optional.empty();
        }
    }

    protected void printDebugMessage(Object element, String jexlExpression) {
        log.trace("Found match: '{}' matches '{}'", element, jexlExpression);
    }

    protected Map<String, Object> buildJexlMapContext(Object element, Optional<String> key) {
        final Map<String, Object> mapContext = new HashMap<>();

        mapContext.put("element", element);
        if (element != null) {
            mapContext.put("type", element.getClass().getSimpleName());
            mapContext.put("content", getContent(element));
        }

        return mapContext;
    }

    protected String getContent(Object element) {
        return element.toString();
    }

    protected JexlExpression buildExpression(String jexlExpression, Object element, MapContext mapContext) {
        final int hashCode = jexlExpression.hashCode();
        if (JEXL_EXPRESSION_CACHE.containsKey(hashCode)) {
            return JEXL_EXPRESSION_CACHE.get(hashCode);
        }

        JexlBuilder jexlBuilder = new JexlBuilder()
            .namespaces(NAMESPACE_MAP)
            .permissions(JexlPermissions.UNRESTRICTED)
            .strict(true);
        jexlBuilder.options().setStrictArithmetic(false);
        final JexlEngine jexlEngine = jexlBuilder.create();
        final JexlExpression expression = jexlEngine.createExpression(jexlExpression);
        JEXL_EXPRESSION_CACHE.put(hashCode, expression);
        return expression;
    }

    public static void registerAdditionalNamespace(String namespace, Object value) {
        NAMESPACE_MAP.put(namespace, value);
    }

    public static void deregisterNamespace(String namespace) {
        NAMESPACE_MAP.remove(namespace);
    }
}
