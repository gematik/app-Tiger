/*
 * Copyright (c) 2022 gematik GmbH
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;

@Slf4j
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
