/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class TestTokenSubstituteHelper {

    private Map<String, Object> ctxt;

    @BeforeEach
    public void init() {
        ctxt = Map.of("key1", "value1", "key2", "KEY2VALUE");
    }

    @ParameterizedTest
    @CsvSource(value = {
        "value1${TESTENV.key2}textblabla , TESTENV, value1KEY2VALUEtextblabla",
        "value1${TESTENV.key2}text${TESTENV.key1}blabla, TESTENV, value1KEY2VALUEtextvalue1blabla",
        "value1${TESTENV.key2}text${TESTENV.key3}blabla, TESTENV, value1KEY2VALUEtext${TESTENV.key3}blabla"}
    )
    public void testSubstituteTokenOK(String stringToSubstitute, String token, String expectedString) {
        assertThat(TokenSubstituteHelper
            .substitute(stringToSubstitute, token, ctxt))
            .isEqualTo(expectedString);
    }
}
