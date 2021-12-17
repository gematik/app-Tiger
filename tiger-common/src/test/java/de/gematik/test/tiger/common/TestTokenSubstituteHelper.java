/*
 * Copyright (c) 2021 gematik GmbH
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
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestTokenSubstituteHelper {
    // TODO TGR-250 replace next three methods with data provided methods
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
