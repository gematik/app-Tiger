/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addSomeMessagesToTigerTestHooks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
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
}
