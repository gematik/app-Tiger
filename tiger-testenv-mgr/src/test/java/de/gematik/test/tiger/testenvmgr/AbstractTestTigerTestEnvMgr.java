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

package de.gematik.test.tiger.testenvmgr;

import static org.mockserver.model.HttpRequest.request;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.springframework.util.SocketUtils;

@Slf4j
@Getter
public abstract class AbstractTestTigerTestEnvMgr {

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private Expectation downloadExpectation;
    private byte[] winstoneBytes;

    @AfterAll
    public static void resetProperties() {
        System.clearProperty("mockserver.port");
    }

    @BeforeEach
    public void startServer() throws IOException {
        if (mockServer != null) {
            return;
        }
        log.info("Booting MockServer...");
        mockServer = new MockServer(SocketUtils.findAvailableTcpPorts(1).first());
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

        final File winstoneFile = new File("target/winstone.jar");
        if (!winstoneFile.exists()) {
            throw new RuntimeException("winstone.jar not found in target-folder. " +
                "Did you run mvn generate-test-resources? (It should be downloaded automatically)");
        }
        winstoneBytes = FileUtils.readFileToByteArray(winstoneFile);

        downloadExpectation = mockServerClient.when(request()
                .withPath("/download"))
            .respond(req -> HttpResponse.response()
                .withBody(winstoneBytes))[0];

        System.setProperty("mockserver.port", Integer.toString(mockServer.getLocalPort()));
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initialize();
    }

    @BeforeEach
    public void printName(TestInfo testInfo) {
        TigerGlobalConfiguration.reset();
        if (testInfo.getTestMethod().isPresent()) {
            log.info(Ansi.colorize("Starting " + testInfo.getTestMethod().get().getName(), RbelAnsiColors.GREEN_BOLD));
        } else {
            log.warn(Ansi.colorize("Starting UNKNOWN step", RbelAnsiColors.GREEN_BOLD));
        }
        System.clearProperty("TIGER_TESTENV_CFGFILE");
    }

    @AfterEach
    public void resetConfiguration() {
        TigerGlobalConfiguration.reset();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // helper methods
    //
    // -----------------------------------------------------------------------------------------------------------------

    public void createTestEnvMgrSafelyAndExecute(ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
        TigerTestEnvMgr envMgr = null;
        try {
            TigerGlobalConfiguration.initialize();
            envMgr = new TigerTestEnvMgr();
            testEnvMgrConsumer.accept(envMgr);
        } finally {
            if (envMgr != null) {
                envMgr.shutDown();
            }
        }
    }
}