/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.io.Files;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
@NotThreadSafe
public class TigerStandaloneProxyTest extends AbstractTestTigerTestEnvMgr {

  static File standaloneJar;
  private static UnirestInstance unirestInstance;

  @BeforeAll
  public static void getJarFile() throws IOException {
    standaloneJar =
        Arrays.stream(new File("../tiger-standalone-proxy/target").listFiles())
            .filter(f -> f.getName().matches("tiger-standalone-proxy-.*\\.jar"))
            .filter(f -> !f.getName().contains("javadoc"))
            .findFirst()
            .get();

    unirestInstance = Unirest.spawnInstance();
    unirestInstance.config().proxy(null);

    File newFile =
        new File(standaloneJar.getParentFile().getAbsolutePath() + File.separatorChar + "test.jar");
    FileUtils.copyFile(standaloneJar, newFile);
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
              servers:
                winstoneStandaloneProxy120:
                  type: externalJar
                  startupTimeoutSec: 60
                  source:
                    - local:target/winstone.jar
                  healthcheckUrl: http://127.0.0.1:${free.port.120}
                  externalJarOptions:
                    arguments:
                      - --httpPort=${free.port.120}
                      - --webroot=.
              """,
      skipEnvironmentSetup = true)
  void testCreateStandaloneProxyAsExternalJarViaExternalProcess(TigerTestEnvMgr envMgr) {
    setUpEnvAndExecuteWithSecureShutdown(
        "12",
        () -> {
          Process proc;
          try {
            proc =
                new ProcessBuilder()
                    .directory(new File("target/12"))
                    .command("java", "-jar", standaloneJar.getAbsolutePath())
                    .inheritIO()
                    .start();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          try {
            await("checkProxyOnline for offset '12'")
                .atMost(50, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> checkProxyOnline("12"));
          } finally {
            proc.destroy();
            if (proc.isAlive()) {
              proc.destroyForcibly();
            }
          }
        },
        envMgr);
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                servers:
                  winstoneStandaloneProxy110:
                    type: externalJar
                    startupTimeoutSec: 60
                    source:
                      - local:target/winstone.jar
                    healthcheckUrl: http://127.0.0.1:${free.port.110}
                    externalJarOptions:
                      arguments:
                        - --httpPort=${free.port.110}
                        - --webroot=.
                  externalProxy110:
                    type: externalJar
                    startupTimeoutSec: 60
                    source:
                      - local:test.jar
                    healthcheckUrl: http://127.0.0.1:${free.port.115}
                    externalJarOptions:
                      workingDir: ../tiger-standalone-proxy/target
                      arguments:
                        - --spring.config.location=../../tiger-testenv-mgr/target/11/
                """,
      skipEnvironmentSetup = true)
  void testCreateStandaloneProxyAsExternalJarViaTestEnvMgr(TigerTestEnvMgr envMgr) {
    setUpEnvAndExecuteWithSecureShutdown(
        "11",
        () -> {
          await("checkProxyOnline for offset '11'")
              .atMost(50, TimeUnit.SECONDS)
              .pollInterval(500, TimeUnit.MILLISECONDS)
              .until(() -> checkProxyOnline("11"));
        },
        envMgr);
  }

  private void setUpEnvAndExecuteWithSecureShutdown(
      String offset, Runnable test, TigerTestEnvMgr envMgr) {
    try {
      prepareApplicationConfigForProxy(offset);
      envMgr.setUpEnvironment();
      test.run();
    } finally {
      envMgr.shutDown();
    }
  }

  private void prepareApplicationConfigForProxy(String offset) {
    String appyaml =
        "tigerProxy:\n"
            + "      adminPort: "
            + TigerGlobalConfiguration.readString("free.port." + offset + "4")
            + "\n"
            + "      proxyPort: "
            + TigerGlobalConfiguration.readString("free.port." + offset + "5")
            + "\n"
            + "      proxyRoutes: \n"
            + "      - from: /\n"
            + "        to: http://127.0.0.1:"
            + TigerGlobalConfiguration.readString("free.port." + offset + "0")
            + "\n";

    try {
      File f = Path.of("target", offset, "application.yml").toFile();
      Files.createParentDirs(f);
      Files.asCharSink(f, StandardCharsets.UTF_8).write(appyaml);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private Boolean checkProxyOnline(String offset) {
    try {
      log.info(
          "connecting to admin api:"
              + "http://127.0.0.1:"
              + TigerGlobalConfiguration.readString("free.port." + offset + "5")
              + "/");
      HttpResponse<String> response =
          unirestInstance
              .get(
                  "http://127.0.0.1:"
                      + TigerGlobalConfiguration.readString("free.port." + offset + "5")
                      + "/")
              .asString();
      // check routing to winstone works
      if (response.isSuccess()) {
        assertThat(response.getBody()).contains("Directory:").contains("winstone.jar");
        log.info(
            "connecting to proxy api:"
                + "http://127.0.0.1:"
                + TigerGlobalConfiguration.readString("free.port." + offset + "4")
                + "/webui");
        // check webui is "working"
        response =
            unirestInstance
                .get(
                    "http://127.0.0.1:"
                        + TigerGlobalConfiguration.readString("free.port." + offset + "4")
                        + "/webui")
                .asString();
        if (response.isSuccess()) {
          assertThat(response.getBody()).contains("Tiger Proxy Log").contains("Proxy port");
          return true;
        } else {
          log.warn("Proxy port returns status {}", response.getStatus());
        }
      } else {
        log.warn("Admin port returns status {}", response.getStatus());
      }
    } catch (Exception e) {
      log.warn("Accepting exception {}", e.getMessage());
    }
    return false;
  }
}
