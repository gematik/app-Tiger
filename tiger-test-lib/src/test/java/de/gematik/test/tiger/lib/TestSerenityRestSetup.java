/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import net.serenitybdd.rest.SerenityRest;
import org.junit.Test;
import org.springframework.util.SocketUtils;

import java.net.URI;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class TestSerenityRestSetup {

    @Test
    public void useNonExistentProxy_ExceptionMessageShouldContainRequestInformation() throws Exception {
        withEnvironmentVariable("TIGER_ACTIVE", "1")
            .execute(() -> TigerDirector.beforeTestRun());

        final String proxy = "http://localhost:" + SocketUtils.findAvailableTcpPort();
        final String serverUrl = "http://localhost:5342/foobar";
        assertThatThrownBy(() -> SerenityRest
            .with().proxy(proxy)
            .get(new URI(serverUrl)))
            .hasMessageContaining(proxy)
            .hasMessageContaining("GET " + serverUrl);
    }
}
