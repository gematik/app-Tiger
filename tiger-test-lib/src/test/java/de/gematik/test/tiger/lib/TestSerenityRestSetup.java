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
