/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

@Slf4j
@Getter
@WireMockTest(httpsEnabled = true)
public abstract class AbstractTestTigerTestEnvMgr {

  private static byte[] winstoneBytes;

  @AfterAll
  public static void resetProperties() {
    System.clearProperty("wiremock.port");
  }

  @BeforeAll
  public static void startServer(WireMockRuntimeInfo runtimeInfo) throws IOException {
    log.info("Started Wiremock on port {} (http)", runtimeInfo.getHttpPort());
    final File winstoneFile = new File("target/winstone.jar");
    if (!winstoneFile.exists()) {
      throw new RuntimeException(
          "winstone.jar not found in target-folder. "
              + "Did you run mvn generate-test-resources? (It should be downloaded automatically)");
    }
    winstoneBytes = FileUtils.readFileToByteArray(winstoneFile);

    System.setProperty("wiremock.port", Integer.toString(runtimeInfo.getHttpPort()));
  }

  @AfterEach
  @BeforeEach
  public void resetConfiguration(WireMockRuntimeInfo runtimeInfo) {
    TigerGlobalConfiguration.reset();
    runtimeInfo.getWireMock().register(get("/download").willReturn(ok().withBody(winstoneBytes)));
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // helper methods
  //
  // -----------------------------------------------------------------------------------------------------------------

  public static void createTestEnvMgrSafelyAndExecute(
      String configurationFilePath, ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
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
      }
    }
  }

  public void createTestEnvMgrSafelyAndExecute(
      ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer, String configurationFilePath) {
    createTestEnvMgrSafelyAndExecute(configurationFilePath, testEnvMgrConsumer);
  }

  public static void createTestEnvMgrSafelyAndExecute(
      ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
    createTestEnvMgrSafelyAndExecute("", testEnvMgrConsumer);
  }

  public TigerTestEnvMgr mockTestEnvMgr() {
    final TigerTestEnvMgr mockMgr = mock(TigerTestEnvMgr.class);
    doReturn(mock(ThreadPoolExecutor.class)).when(mockMgr).getCachedExecutor();
    return mockMgr;
  }
}
