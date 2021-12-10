/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestTokenSubstituteHelper {
    // TODO replace next three methods with data provided methods
    @Test
    public void testSubstituteTokenOK() {
        final Map<String, Object> ctxt = Map.of("key1", "value1", "key2", "KEY2VALUE");
        assertThat(TokenSubstituteHelper
            .substitute("value1${TESTENV.key2}textblabla", "TESTENV", ctxt))
            .isEqualTo("value1KEY2VALUEtextblabla");
    }

    @Test
    public void testSubstituteToken2OK() {
        final Map<String, Object> ctxt = Map.of("key1", "value1", "key2", "KEY2VALUE");
        assertThat(
            TokenSubstituteHelper
                .substitute("value1${TESTENV.key2}text${TESTENV.key1}blabla", "TESTENV", ctxt))
            .isEqualTo("value1KEY2VALUEtextvalue1blabla");
    }

    @Test
    public void testSubstituteTokenUnsetValueOK() {
        final Map<String, Object> ctxt = Map.of("key1", "value1", "key2", "KEY2VALUE");
        assertThat(
            TokenSubstituteHelper
                .substitute("value1${TESTENV.key2}text${TESTENV.key3}blabla", "TESTENV", ctxt))
            .isEqualTo("value1KEY2VALUEtext${TESTENV.key3}blabla");
    }

}
