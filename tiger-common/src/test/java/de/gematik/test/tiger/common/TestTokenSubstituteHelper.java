/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TestTokenSubstituteHelper {

    @BeforeEach
    void init() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.putValue("key1", "value1");
        TigerGlobalConfiguration.putValue("key2", "KEY2VALUE");
        TigerGlobalConfiguration.putValue("foo.bar", "FOOBARVALUE");
        TigerGlobalConfiguration.putValue("give.me.a.foo", "foo");
        TigerGlobalConfiguration.putValue("some.boolean.value", "true");
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
        "fdsafdas${${foo.bar} , fdsafdas${FOOBARVALUE",
        // mix JEXL and TigerGlobalConfiguration
        "give me a ${!{'give.me' + '.a.foo'}}, give me a foo",
        "${!{'give.me' + '.a.foo'}}, foo",
        "!{not ${some.boolean.value}}, false",
        // non resolvable placeholders
        "${non.existing.value}, ${non.existing.value}",
        "!{'blub' + ${non.existing.value}}, !{'blub' + ${non.existing.value}}"
    })
    void testSubstituteTokenOK(String stringToSubstitute, String expectedString) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
            .isEqualTo(expectedString);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "is it !{10 == 0} or not? , is it false or not?",
        "!{file('src/test/resources/helloworld.txt')},  Hello World!",
        "!{not ${some.boolean.value}},  false"
    })
    void testFunctionExecution(String stringToSubstitute, String expectedString) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
            .isEqualTo(expectedString);
    }

    @Test
    void testRegisteringAndDeregisteringAdditionalNamespaces() {
        final String expression = "!{foo:bar()}";

        assertThat(TigerGlobalConfiguration.resolvePlaceholders(expression))
            .isEqualTo(expression);

        TigerJexlExecutor.registerAdditionalNamespace("foo", new FooBarClass());

        assertThat(TigerGlobalConfiguration.resolvePlaceholders(expression)).isEqualTo("realResult");

        TigerJexlExecutor.deregisterNamespace("foo");

        assertThat(TigerGlobalConfiguration.resolvePlaceholders(expression))
            .isEqualTo(expression);
    }

    @Test
    void testCombinedExpressions() {
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
