/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
public class TigerStandaloneProxyTest extends AbstractTestTigerTestEnvMgr {

  static File standaloneJar;

  @BeforeAll
  public static void getJarFile() throws IOException {
    standaloneJar =
        Arrays.stream(new File("../tiger-standalone-proxy/target").listFiles())
            .filter(f -> f.getName().matches("tiger-standalone-proxy-.*\\.jar"))
            .filter(f -> !f.getName().contains("javadoc"))
            .findFirst()
            .get();

    File newFile =
        new File(standaloneJar.getParentFile().getAbsolutePath() + File.separatorChar + "test.jar");
    FileUtils.copyFile(standaloneJar, newFile);
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  winstone:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - local:target/winstone.jar\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - --httpPort=${free.port.0}\n"
              + "        - --webroot=.\n",
      skipEnvironmentSetup = true)
  void testCreateStandaloneProxyAsExternalJarViaExternalProcess(TigerTestEnvMgr envMgr) {
    setUpEnvAndExecuteWithSecureShutdown(
        () -> {
          Process proc = null;
          try {
            proc =
                new ProcessBuilder()
                    .directory(new File("target"))
                    .command("java", "-jar", standaloneJar.getAbsolutePath())
                    .inheritIO()
                    .start();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

          try {
            await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(this::checkProxyOnline);
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
          "servers:\n"
              + "  winstone:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - local:target/winstone.jar\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - --httpPort=${free.port.0}\n"
              + "        - --webroot=.\n"
              + "  externalProxy:\n"
              + "    type: externalJar\n"
              + "    startupTimeoutSec: 40\n"
              + "    source:\n"
              + "      - local:test.jar\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.5}\n"
              + "    externalJarOptions:\n"
              + "      workingDir: ../tiger-standalone-proxy/target/\n"
              + "      arguments:\n"
              + "        - --spring.config.location=../../tiger-testenv-mgr/target/\n",
      skipEnvironmentSetup = true)
  void testCreateStandaloneProxyAsExternalJarViaTestEnvMgr(TigerTestEnvMgr envMgr)
      throws IOException {
    setUpEnvAndExecuteWithSecureShutdown(
        () -> {
          await()
              .atMost(50, TimeUnit.SECONDS)
              .pollInterval(500, TimeUnit.MILLISECONDS)
              .until(() -> checkProxyOnline());
        },
        envMgr);
  }

  private void setUpEnvAndExecuteWithSecureShutdown(Runnable test, TigerTestEnvMgr envMgr) {
    try {
      prepareApplicationConfigForProxy();
      envMgr.setUpEnvironment();
      test.run();
    } finally {
      envMgr.shutDown();
    }
  }

  private void prepareApplicationConfigForProxy() {
    String appyaml =
        "tigerProxy:\n"
            + "      adminPort: "
            + TigerGlobalConfiguration.readString("free.port.4")
            + "\n"
            + "      proxyPort: "
            + TigerGlobalConfiguration.readString("free.port.5")
            + "\n"
            + "      proxyRoutes: \n"
            + "      - from: http://127.0.0.1:"
            + TigerGlobalConfiguration.readString("free.port.5")
            + "\n"
            + "        to: http://127.0.0.1:"
            + TigerGlobalConfiguration.readString("free.port.0")
            + "\n";

    try {
      Files.write(appyaml, new File("target/application.yml"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private Boolean checkProxyOnline() {
    try {
      log.info(
          "connecting to admin api:"
              + "http://127.0.0.1:"
              + TigerGlobalConfiguration.readString("free.port.5")
              + "/");
      HttpResponse<String> response =
          Unirest.get(
                  "http://127.0.0.1:" + TigerGlobalConfiguration.readString("free.port.5") + "/")
              .asString();
      // check routing to winstone works
      if (response.isSuccess()) {
        assertThat(response.getBody()).contains("Directory:").contains("winstone.jar");
        log.info(
            "connecting to proxy api:"
                + "http://127.0.0.1:"
                + TigerGlobalConfiguration.readString("free.port.4")
                + "/webui");
        // check webui is "working"
        response =
            Unirest.get(
                    "http://127.0.0.1:"
                        + TigerGlobalConfiguration.readString("free.port.4")
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
