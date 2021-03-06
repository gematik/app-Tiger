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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;

@Slf4j
@Getter
public abstract class AbstractTestTigerTestEnvMgr {

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static Expectation downloadExpectation;
    private static byte[] winstoneBytes;

    @AfterAll
    public static void resetProperties() {
        System.clearProperty("mockserver.port");
    }

    @BeforeAll
    public static void startServer() throws IOException {
        log.info("Booting MockServer...");
        mockServer = new MockServer(TigerGlobalConfiguration.readIntegerOptional("free.port.200").orElseThrow());
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

    public void createTestEnvMgrSafelyAndExecute(String configurationFilePath,
        ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
        TigerTestEnvMgr envMgr = null;
        try {
            if (StringUtils.isEmpty(configurationFilePath)) {
                TigerGlobalConfiguration.initialize();
            } else {
                TigerGlobalConfiguration.initializeWithCliProperties(
                    Map.of("TIGER_TESTENV_CFGFILE", configurationFilePath));
            }
            envMgr = new TigerTestEnvMgr();
            testEnvMgrConsumer.accept(envMgr);
        } finally {
            if (envMgr != null) {
                envMgr.shutDown();
                TigerGlobalConfiguration.reset();
            }
        }
    }

    public void createTestEnvMgrSafelyAndExecute(ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer, String configurationFilePath) {
        createTestEnvMgrSafelyAndExecute(configurationFilePath, testEnvMgrConsumer);
    }
    public void createTestEnvMgrSafelyAndExecute(ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
        createTestEnvMgrSafelyAndExecute("", testEnvMgrConsumer);
    }

    public TigerTestEnvMgr mockTestEnvMgr() {
        final TigerTestEnvMgr mockMgr = mock(TigerTestEnvMgr.class);
        doReturn(mock(ThreadPoolExecutor.class))
            .when(mockMgr).getExecutor();
        return mockMgr;
    }
}
