/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import de.gematik.test.tiger.lib.serenityRest.SerenityRestUtils;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

@Slf4j
public class TestSerenityRestSetup {

    @Test
    public void useNonExistentProxy_ExceptionMessageShouldContainRequestInformation() {
        SerenityRestUtils.setupSerenityRest(1234);

        final String proxy = "http://localhost:" + SocketUtils.findAvailableTcpPort();
        final String serverUrl = "http://localhost:5342/foobar";
        assertThatThrownBy(() -> SerenityRest
            .with().proxy(proxy)
            .get(new URI(serverUrl)))
            .hasMessageContaining(proxy)
            .hasMessageContaining("GET " + serverUrl);
    }
}
