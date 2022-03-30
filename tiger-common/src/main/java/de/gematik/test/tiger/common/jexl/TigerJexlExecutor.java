/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;

public class TigerJexlExecutor {

    private static final Map<String, Object> NAMESPACE_MAP = new HashMap<>();

    static {
        NAMESPACE_MAP.put(null, InlineJexlToolbox.class);
    }

    public static String execute(String value) {
        final JexlExpression expression = buildExpression(value);
        final MapContext mapContext = new MapContext();

        return Optional.ofNullable(expression.evaluate(mapContext))
            .map(Object::toString)
            .orElseThrow(() -> new TigerJexlException(
                "Error while executing expression, got null result. Expression evaluated: " + value));
    }

    private static JexlExpression buildExpression(String jexlExpression) {
        final JexlEngine jexlEngine = new JexlBuilder()
            .namespaces(NAMESPACE_MAP)
            .strict(true)
            .create();
        return jexlEngine
            .createExpression(jexlExpression);
    }

    public static void registerAdditionalNamespace(String namespace, Object value) {
        NAMESPACE_MAP.put(namespace, value);
    }

    public static void deregisterNamespace(String namespace) {
        NAMESPACE_MAP.remove(namespace);
    }

    private static class TigerJexlException extends RuntimeException {

        public TigerJexlException(String s) {
            super(s);
        }
    }
}
