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

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import org.apache.commons.jexl3.JexlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestTokenSubstituteHelper {

    @BeforeEach
    public void init() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("key1", "value1");
        TigerGlobalConfiguration.putValue("key2", "KEY2VALUE");
        TigerGlobalConfiguration.putValue("foo.bar", "FOOBARVALUE");
        TigerGlobalConfiguration.putValue("give.me.a.foo", "foo");
    }

    @ParameterizedTest
    @CsvSource(value = {
        // standard substitutions
        "value1${key2}textblabla , value1KEY2VALUEtextblabla",
        "value1${key2}text${key1}blabla, value1KEY2VALUEtextvalue1blabla",
        "value1${key2}text${key3}blabla, value1KEY2VALUEtext${key3}blabla",
        "value1${FOO_BAR}textblabla , value1FOOBARVALUEtextblabla",
        "value1${foo.bar}textblabla , value1FOOBARVALUEtextblabla",
        // nested values
        "${${give.me.a.foo}.bar} , FOOBARVALUE",
        "${${give.me.a.${give.me.a.${give.me.a.foo}}}.bar} , FOOBARVALUE",
        "hallo${${give.me.a.foo}.bar} , halloFOOBARVALUE",
        "hallo${${give.me.a.foo}.bar}blub${key1} , halloFOOBARVALUEblubvalue1",
        // test for partial value-markers: they should not interfere with the substitution
        "${foo.bar}} , FOOBARVALUE}",
        "${${foo.bar} , ${FOOBARVALUE",
        "${foo.bar}}fds , FOOBARVALUE}fds",
        "fdsafdas${${foo.bar} , fdsafdas${FOOBARVALUE"
    }
    )
    public void testSubstituteTokenOK(String stringToSubstitute, String expectedString) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
            .isEqualTo(expectedString);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "is it !{10 == 0} or not? , is it false or not?",
        "!{file('src/test/resources/helloworld.txt')},  Hello World!"
    })
    public void testFunctionExecution(String stringToSubstitute, String expectedString) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
            .isEqualTo(expectedString);
    }

    @Test
    public void testRegisteringAndDeregisteringAdditionalNamespaces() {
        final String expression = "!{foo:bar()}";

        assertThatThrownBy(() -> TigerGlobalConfiguration.resolvePlaceholders(expression))
            .isInstanceOf(JexlException.class);

        TigerJexlExecutor.registerAdditionalNamespace("foo", new FooBarClass());

        assertThat(TigerGlobalConfiguration.resolvePlaceholders(expression)).isEqualTo("realResult");

        TigerJexlExecutor.deregisterNamespace("foo");

        assertThatThrownBy(() -> TigerGlobalConfiguration.resolvePlaceholders(expression))
            .isInstanceOf(JexlException.class);
    }

    @Test
    public void testCombinedExpressions() {
        TigerJexlExecutor.registerAdditionalNamespace("foo", new FooBarClass());
        assertThat(TigerGlobalConfiguration.resolvePlaceholders("!{foo:asPlaceholder('key1')}"))
            .isEqualTo("value1");
        TigerJexlExecutor.deregisterNamespace("foo");
    }

    public static class FooBarClass {

        public String bar() {
            return "realResult";
        }

        public String asPlaceholder(String value) {
            return "${" + value + "}";
        }
    }
}
