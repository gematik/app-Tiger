/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;

@Slf4j
public class TigerJexlExecutor {

    public static boolean ACTIVATE_JEXL_DEBUGGING = false;
    public static Supplier<TigerJexlExecutor> executorSupplier = TigerJexlExecutor::new;
    private static final Map<String, Object> NAMESPACE_MAP = new HashMap<>();

    static {
        NAMESPACE_MAP.put(null, InlineJexlToolbox.class);
    }

    public static boolean matchesAsJexlExpression(Object element, String jexlExpression) {
        return executorSupplier.get()
            .matchesAsJexlExpressionInternal(element, jexlExpression);
    }

    public static boolean matchesAsJexlExpression(Object element, String jexlExpression, Optional<String> keyInParentElement) {
        return matchesAsJexlExpression(jexlExpression,
            new TigerJexlContext()
                .withKey(keyInParentElement.orElse(null))
                .withCurrentElement(element));
    }

    public static boolean matchesAsJexlExpression(String jexlExpression, TigerJexlContext context) {
        return executorSupplier.get()
            .matchesAsJexlExpressionInternal(jexlExpression, context);
    }

    public static Optional<Object> evaluateJexlExpression(String jexlExpression, TigerJexlContext context) {
        return executorSupplier.get()
            .evaluateJexlExpressionInternal(jexlExpression, context);
    }

    private boolean matchesAsJexlExpressionInternal(Object element, String jexlExpression) {
        return matchesAsJexlExpression(jexlExpression, new TigerJexlContext()
            .withCurrentElement(element));
    }

    private boolean matchesAsJexlExpressionInternal(String jexlExpression, TigerJexlContext context) {
        final boolean result = evaluateJexlExpression(jexlExpression, context)
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .orElse(false);

        if (result && ACTIVATE_JEXL_DEBUGGING) {
            printDebugMessage(context.getCurrentElement(), jexlExpression);
        }

        return result;
    }

    private Optional<Object> evaluateJexlExpressionInternal(String jexlExpression, TigerJexlContext context) {
        try {
            NAMESPACE_MAP.forEach(context::set);
            context.putAll(buildJexlMapContext(
                context.getCurrentElement(),
                Optional.ofNullable(context.getKey())
            ));
            final JexlExpression expression = buildExpression(jexlExpression, context);

            return Optional.ofNullable(expression.evaluate(context));
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

    protected JexlExpression buildExpression(String jexlExpression, TigerJexlContext mapContext) {
        JexlBuilder jexlBuilder = new JexlBuilder()
            .namespaces(NAMESPACE_MAP)
            .permissions(JexlPermissions.UNRESTRICTED)
            .strict(true);
        jexlBuilder.options().setStrictArithmetic(false);
        final JexlEngine jexlEngine = jexlBuilder.create();
        final JexlExpression expression = jexlEngine.createExpression(jexlExpression);
        return expression;
    }

    public static void registerAdditionalNamespace(String namespace, Object value) {
        NAMESPACE_MAP.put(namespace, value);
    }

    public static void deregisterNamespace(String namespace) {
        NAMESPACE_MAP.remove(namespace);
    }
}
