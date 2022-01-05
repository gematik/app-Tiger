/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.Test;
import org.springframework.util.SocketUtils;

@Slf4j
public class TestSerenityRestSetup {

    @Test
    public void useNonExistentProxy_ExceptionMessageShouldContainRequestInformation() throws Exception {
        withEnvironmentVariable("TIGER_ACTIVE", "1")
            .and("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersNoForwardProxy.yaml")
            .execute(() -> TigerDirector.startMonitorUITestEnvMgrAndTigerProxy(new TigerLibConfig()));

        final String proxy = "http://localhost:" + SocketUtils.findAvailableTcpPort();
        final String serverUrl = "http://localhost:5342/foobar";
        assertThatThrownBy(() -> SerenityRest
            .with().proxy(proxy)
            .get(new URI(serverUrl)))
            .hasMessageContaining(proxy)
            .hasMessageContaining("GET " + serverUrl);
    }
}
