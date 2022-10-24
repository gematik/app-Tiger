package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class RBelValidatorGlueTest {

    @Test
    void testFindLastRequestWithNoRequestFound() {
        RBelValidatorGlue.getRbelValidator().clearRBelMessages();
        assertThatThrownBy(
            () -> new RBelValidatorGlue().findLastRequest())
            .hasMessageContaining("No Request found");
    }
}
