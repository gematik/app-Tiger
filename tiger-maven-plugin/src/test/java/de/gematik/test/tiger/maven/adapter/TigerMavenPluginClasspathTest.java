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
package de.gematik.test.tiger.maven.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.maven.adapter.mojos.TestEnvironmentMojo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TigerMavenPluginClasspathTest {

  private static final String TIGER_TESTENV_CFGFILE = "tiger.testenv.cfgfile";

  private MojoTestSetup mojoTestSetup;
  private Path tempCfgFile;

  @BeforeEach
  void setUp() {
    TigerDirector.testUninitialize();
    TigerGlobalConfiguration.reset();
    mojoTestSetup = new MojoTestSetup();
  }

  @AfterEach
  void tearDown() {
    TigerDirector.testUninitialize();
    TigerGlobalConfiguration.reset();
    System.clearProperty(TIGER_TESTENV_CFGFILE);
    if (tempCfgFile != null) {
      try {
        Files.deleteIfExists(tempCfgFile);
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }
  }

  @Test
  void testShouldPassWhenMojoUsesCorrectClassLoader() {
    TestEnvironmentMojo mojo = mojoTestSetup.setupMojo();
    mojo.setAutoShutdownAfterSeconds(5);

    assertDoesNotThrow(mojo::execute);
  }

  @Test
  void testShouldPassWhenTwoServersUseFluentTraceLoggerApi() {
    TestEnvironmentMojo mojo = mojoTestSetup.setupMojo();
    mojo.setAutoShutdownAfterSeconds(5);

    tempCfgFile = createTempTigerConfig();
    System.setProperty(TIGER_TESTENV_CFGFILE, tempCfgFile.toAbsolutePath().toString());

    assertDoesNotThrow(mojo::execute);
  }

  private Path createTempTigerConfig() {
    try {
      Path path = Files.createTempFile("tiger-mojo-test", ".yaml");
      Files.writeString(
          path,
          """
          localProxyActive: false
          servers:
            externalURL:
              type: fluentTraceLogTestServer
            externalJar:
              type: fluentTraceLogTestServer
          """);
      return path;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
