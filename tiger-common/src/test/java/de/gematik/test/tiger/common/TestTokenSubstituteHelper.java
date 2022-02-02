/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestTokenSubstituteHelper {

    @BeforeEach
    public void init() {
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.putValue("key1", "value1");
        TigerGlobalConfiguration.putValue("key2", "KEY2VALUE");
        TigerGlobalConfiguration.putValue("foo.bar", "FOOBARVALUE");
    }

    @ParameterizedTest
    @CsvSource(value = {
        "value1${key2}textblabla , value1KEY2VALUEtextblabla",
        "value1${key2}text${key1}blabla, value1KEY2VALUEtextvalue1blabla",
        "value1${key2}text${key3}blabla, value1KEY2VALUEtext${key3}blabla",
        "value1${FOO_BAR}textblabla , value1FOOBARVALUEtextblabla",
        "value1${foo.bar}textblabla , value1FOOBARVALUEtextblabla"
    }
    )
    public void testSubstituteTokenOK(String stringToSubstitute, String expectedString) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
            .isEqualTo(expectedString);
    }
}
