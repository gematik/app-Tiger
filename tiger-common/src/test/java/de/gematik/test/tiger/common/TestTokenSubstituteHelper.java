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
