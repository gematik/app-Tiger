/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import java.util.*;
import java.util.function.Supplier;
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
        final List<Object> resultList = executorSupplier.get()
            .evaluateJexlExpressionInternal(jexlExpression, context);
        if (resultList.size() > 1) {
            throw new TigerJexlException("Evaluated '"+jexlExpression+"' and got more then one result. Expected one ore zero results.");
        }
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        } else {
            return Optional.empty();
        }
    }

    private boolean matchesAsJexlExpressionInternal(Object element, String jexlExpression) {
        return matchesAsJexlExpression(jexlExpression, new TigerJexlContext()
            .withCurrentElement(element));
    }

    private boolean matchesAsJexlExpressionInternal(String jexlExpression, TigerJexlContext context) {
        final boolean result = executorSupplier.get()
            .evaluateJexlExpressionInternal(jexlExpression, context).stream()
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .reduce(Boolean.FALSE, Boolean::logicalOr);

        if (result && ACTIVATE_JEXL_DEBUGGING) {
            printDebugMessage(context.getCurrentElement(), jexlExpression);
        }

        return result;
    }

    private List<Object> evaluateJexlExpressionInternal(final String jexlExpression, final TigerJexlContext externalContext) {
        try {
            final TigerJexlContext contextMap = buildJexlMapContext(
                externalContext.getCurrentElement(),
                Optional.ofNullable(externalContext.getKey())
            );
            contextMap.putAll(NAMESPACE_MAP);
            contextMap.putAll(externalContext);

            return buildExpressions(jexlExpression, contextMap).stream()
                .map(expression -> expression.evaluate(contextMap))
                .peek(result -> {
                    if (ACTIVATE_JEXL_DEBUGGING) {
                        log.debug("Evaluated \"{}\" to '{}'", jexlExpression, result);
                    }
                })
                .toList();
        } catch (RuntimeException e) {
            if (e instanceof JexlException && (e.getCause() instanceof JexlArithmetic.NullOperand)) {
                return List.of();
            }
            if (e instanceof JexlException && !(e.getCause() instanceof NoSuchElementException)) {
                throw new TigerJexlException("Error while parsing expression '" + jexlExpression + "'", e);
            }
            log.warn("Error during Jexl-Evaluation.", e);
            return List.of();
        }
    }

    protected void printDebugMessage(Object element, String jexlExpression) {
        log.trace("Found match: '{}' matches '{}'", element, jexlExpression);
    }

    protected TigerJexlContext buildJexlMapContext(Object element, Optional<String> key) {
        final TigerJexlContext mapContext = new TigerJexlContext();

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

    protected List<JexlExpression> buildExpressions(String jexlExpression, TigerJexlContext mapContext) {
        return List.of(getJexlEngine().createExpression(jexlExpression));
    }

    private static JexlEngine getJexlEngine() {
        JexlBuilder jexlBuilder = new JexlBuilder()
            .namespaces(NAMESPACE_MAP)
            .permissions(JexlPermissions.UNRESTRICTED)
            .strict(true);
        jexlBuilder.options().setStrictArithmetic(false);
        return jexlBuilder.create();
    }

    public JexlScript buildScript(String jexlScript) {
        return getJexlEngine().createScript(jexlScript);
    }

    public static void registerAdditionalNamespace(String namespace, Object value) {
        NAMESPACE_MAP.put(namespace, value);
    }

    public static void deregisterNamespace(String namespace) {
        NAMESPACE_MAP.remove(namespace);
    }
}
