/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
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

    private Optional<Object> evaluateJexlExpressionInternal(final String jexlExpression, final TigerJexlContext externalContext) {
        try {
            final TigerJexlContext contextMap = buildJexlMapContext(
                externalContext.getCurrentElement(),
                Optional.ofNullable(externalContext.getKey())
            );
            contextMap.putAll(NAMESPACE_MAP);
            contextMap.putAll(externalContext);
            final JexlExpression expression = buildExpression(jexlExpression, contextMap);

            final Object result = expression.evaluate(contextMap);
            if (ACTIVATE_JEXL_DEBUGGING) {
                log.debug("Evaluated \"{}\" to '{}'", jexlExpression, result);
            }

            return Optional.ofNullable(result);
        } catch (RuntimeException e) {
            if (e instanceof JexlException && (e.getCause() instanceof JexlArithmetic.NullOperand)) {
                return Optional.empty();
            }
            if (e instanceof JexlException && !(e.getCause() instanceof NoSuchElementException)) {
                throw new TigerJexlException("Error while parsing expression '" + jexlExpression + "'", e);
            }
            log.warn("Error during Jexl-Evaluation.", e);
            return Optional.empty();
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

    protected JexlExpression buildExpression(String jexlExpression, TigerJexlContext mapContext) {
        return getJexlEngine().createExpression(jexlExpression);
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
