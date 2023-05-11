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

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addSomeMessagesToTigerTestHooks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import org.apache.commons.jexl3.JexlException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class InlineJexlTest {

    {
        RbelMessageValidator.instance.getRbelMessages();
    }

    @BeforeAll
    public static void addSomeMessages() {
        addSomeMessagesToTigerTestHooks();
    }

    @ParameterizedTest
    @CsvSource({
        "!{rbel:lastResponseAsString()}, HTTP\\/1\\.1 200\\X*",
        "!{rbel:lastRequestAsString()}, POST \\/token HTTP\\/1.1\\X*",
        "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$..id_token..wasDecryptable\")}', false",
        "'!{rbel:getValueAtLocationAsString(rbel:lastRequest(), \"$..key_verifier..alg.content\")}', ECDH-ES\\+A256KW"
    })
    void resolveTestStrings(String resolve, String shouldMatch) {
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(resolve))
            .matches(shouldMatch);
    }

    @ParameterizedTest
    @CsvSource({
        "'!{rbel:lastResponse11111111AsString()}'",
        "'!{rbel:lastResponse().isResponse}'",
        "'!{rbel:lastResponse(),,,isResponse}'",
    })
    void resolveTestStringsFailures(String resolve) {
        TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING = true;
        assertThatThrownBy(() -> TigerGlobalConfiguration.resolvePlaceholders(resolve))
            .isInstanceOfAny(JexlException.class, RuntimeException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "'!{rbel:lastResponse(),isResponse'",
        "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$.#%.id_token..wasDecryptable\")}'",
        "'!{rbel:getValueAtLocationAsString(rbel:lastResponse(), \"$..id_token[1]..wasDecryptable\")}'",
    })
    void resolveTestStringsFailuresSilently(String resolve) {
        TigerJexlExecutor.ACTIVATE_JEXL_DEBUGGING = true;
        assertThat(TigerGlobalConfiguration.resolvePlaceholders(resolve)).isEqualTo(resolve);
    }
}
