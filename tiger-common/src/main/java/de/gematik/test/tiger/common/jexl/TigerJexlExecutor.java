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

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;

@Slf4j
public class TigerJexlExecutor {

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
        return matchesAsJexlExpression(jexlExpression, new TigerJexlContext()
            .withCurrentElement(element)
            .withKey(key.orElse(null)));
    }

    public boolean matchesAsJexlExpression(String jexlExpression, TigerJexlContext context) {
        final boolean result = evaluateJexlExpression(jexlExpression, context)
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .orElse(false);

        if (result && ACTIVATE_JEXL_DEBUGGING) {
            printDebugMessage(context.getCurrentElement(), jexlExpression);
        }

        return result;
    }

    public Optional<Object> evaluateJexlExpression(String jexlExpression, Optional<String> key) {
        return evaluateJexlExpression(ELEMENT_STACK.peekFirst(), jexlExpression, key);
    }

    public Optional<Object> evaluateJexlExpression(Object element, String jexlExpression, Optional<String> key) {
        final TigerJexlContext mapContext = new TigerJexlContext(buildJexlMapContext(element, key))
            .withCurrentElement(element)
            .withRootElement(element)
            .withKey(key.orElse(null));
        NAMESPACE_MAP.forEach(mapContext::set);
        return evaluateJexlExpression(jexlExpression, mapContext);
    }

    public Optional<Object> evaluateJexlExpression(String jexlExpression, TigerJexlContext context) {
        try {
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
