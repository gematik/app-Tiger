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
 *
 */

package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.util.InsecureTrustAllManager;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.TestSocketUtils;
import uk.org.webcompere.systemstubs.ThrowingRunnable;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@ExtendWith(OutputCaptureExtension.class)
@Slf4j
class TestTigerDirector {

  @BeforeEach
  void init(TestInfo testInfo) {
    log.info(
        "Now executing test '{}' ({}:{})",
        testInfo.getDisplayName(),
        testInfo.getTestClass().map(Class::getName).orElse("<>"),
        testInfo.getTestMethod().map(Method::getName).orElse("<>"));

    TigerDirector.testUninitialize();
  }

  @AfterEach
  void clearProperties() {
    System.clearProperty("TIGER_TESTENV_CFGFILE");
    System.clearProperty("TIGER_LIB_CLEARENVIRONMENTSTARTUPTRAFFIC");

    TigerDirector.testUninitialize();
  }

  @Test
  void tigerActiveNotSet_TigerDirectorShouldThrowException() {
    System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");

    assertThat(TigerDirector.isInitialized()).isFalse();
    assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
    assertThatThrownBy(TigerDirector::getLocalTigerProxyUrl)
        .isInstanceOf(TigerStartupException.class);
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testDirectorClearsMessagesAfterStartup(boolean clearEnvironmentStartupTraffic) {
    System.setProperty(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyAndWinstone.yaml");
    System.setProperty(
        "TIGER_LIB_CLEARENVIRONMENTSTARTUPTRAFFIC", String.valueOf(clearEnvironmentStartupTraffic));
    executeWithSecureShutdown(
        () -> {
          AtomicInteger listenerClearCalled = addLocalProxyRbelMessageListenerMock();

          TigerDirector.start();

          assertThat(listenerClearCalled.get()).isEqualTo(clearEnvironmentStartupTraffic ? 1 : 0);
        });
  }

  @SneakyThrows
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testDirector_ClearMessagesNoLocalProxy(boolean clearEnvironmentStartupTraffic) {
    System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");
    System.setProperty(
        "TIGER_LIB_CLEARENVIRONMENTSTARTUPTRAFFIC", String.valueOf(clearEnvironmentStartupTraffic));
    executeWithSecureShutdown(
        () -> {
          AtomicInteger listenerClearCalled = addLocalProxyRbelMessageListenerMock();

          TigerDirector.start();

          assertThat(listenerClearCalled.get()).isEqualTo(0);

          assertThat(TigerDirector.getTigerTestEnvMgr().getServers()).hasSize(1);
        });
  }

  private static AtomicInteger addLocalProxyRbelMessageListenerMock() {
    AtomicInteger listenerClearCalled = new AtomicInteger();
    LocalProxyRbelMessageListener.setTestingInstance(
        new LocalProxyRbelMessageListener(
            new RbelMessagesSupplier() {
              @Override
              public void addRbelMessageListener(IRbelMessageListener listener) {}

              @Override
              public Deque<RbelElement> getRbelMessages() {
                return new ArrayDeque<>();
              }
            }) {
          @Override
          public void clearAllMessages() {
            listenerClearCalled.incrementAndGet();
            super.clearAllMessages();
          }
        });
    return listenerClearCalled;
  }

  @Test
  void testDirectorConfigReadandAvailable() {
    System.setProperty(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyAndWinstone.yaml");
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();

          assertThat(TigerDirector.isInitialized()).isTrue();
          assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
          assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail()).isNotNull();
          assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getBaseUrl())
              .startsWith("http://localhost");
          assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getRbelLogger())
              .isNotNull();
          assertThat(
                  TigerDirector.getTigerTestEnvMgr()
                      .getServers()
                      .get("testExternalJar")
                      .getConfiguration()
                      .getExternalJarOptions()
                      .getArguments())
              .hasSize(2);
          assertThat(
                  TigerDirector.getTigerTestEnvMgr()
                      .getServers()
                      .get("testExternalJar")
                      .getConfiguration()
                      .getExternalJarOptions()
                      .getArguments()
                      .get(1))
              .isEqualTo("--webroot=.");
        });
  }

  @SneakyThrows
  @Test
  void testDirectorDisabledProxy(final CapturedOutput capturedOutput) {
    System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");
    executeWithSecureShutdown(
        () -> {
          try {
            TigerDirector.start();

            System.out.println(
                "PROXY:"
                    + System.getProperty("http.proxyHost")
                    + " / "
                    + System.getProperty("https.proxyHost"));
            System.out.println(
                "PORTS:"
                    + System.getProperty("http.proxyPort")
                    + " / "
                    + System.getProperty("https.proxyPort"));

            assertThat(TigerDirector.isInitialized()).isTrue();
            var testenvMgr = TigerDirector.getTigerTestEnvMgr();
            assertThat(testenvMgr).isNotNull();
            assertThatThrownBy(testenvMgr::getLocalTigerProxyOrFail)
                .isInstanceOf(TigerTestEnvException.class);

            final var url = new URL("http://idp-rise-tu-noproxy");

            final URLConnection con = url.openConnection();
            InsecureTrustAllManager.allowAllSsl(con);

            con.setConnectTimeout(1000);

            assertThat(capturedOutput.getOut())
                .contains("SKIPPING TIGER PROXY settings as localProxyActive==false...");
            assertThatThrownBy(con::connect).isInstanceOf(Exception.class);
          } catch (IOException ioe) {
            throw new RuntimeException(ioe);
          }
        });
  }

  @Test
  void testRouteHasHttpsEndpointURLConnection_certificateShouldBeVerified() {
    System.setProperty(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyAndWinstone.yaml");
    executeWithSecureShutdown(
        () -> {
          assertThatNoException()
              .isThrownBy(
                  () -> {
                    TigerDirector.start();

                    System.out.println(
                        "PROXY:"
                            + System.getProperty("http.proxyHost")
                            + " / "
                            + System.getProperty("https.proxyHost"));
                    System.out.println(
                        "PORTS:"
                            + System.getProperty("http.proxyPort")
                            + " / "
                            + System.getProperty("https.proxyPort"));

                    final var url = new URL("https://github.com/");

                    final URLConnection con;
                    con = url.openConnection();
                    InsecureTrustAllManager.allowAllSsl(con);

                    con.connect();
                  });
        });
  }

  @SneakyThrows
  @Test
  void testDirectorFalsePathToYaml() {
    final Path nonExistingPath = Paths.get("non", "existing", "file.yaml");
    new EnvironmentVariables("TIGER_TESTENV_CFGFILE", nonExistingPath.toString())
        .execute(
            () -> {
              assertThatThrownBy(TigerDirector::start)
                  .isInstanceOf(TigerConfigurationException.class)
                  .hasMessageContaining(nonExistingPath.toString());
            });
  }

  @Test
  void testLocalProxyActiveSetByDefault() {
    System.setProperty(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyAndWinstone.yaml");
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();
          assertThat(System.getProperty("http.proxyHost")).isNotNull();
          assertThat(System.getProperty("https.proxyHost")).isNotNull();
          assertThat(System.getProperty("http.proxyPort")).isNotNull();
          assertThat(System.getProperty("https.proxyPort")).isNotNull();
          assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().isLocalProxyActive())
              .isTrue();
          assertThat(SerenityRest.get("http://testExternalJar/").getStatusCode()).isEqualTo(200);
          assertThat(Unirest.get("http://testExternalJar/").asEmpty().getStatus()).isEqualTo(200);
        });
  }

  @Test
  void checkComplexKeyOverriding() throws Exception {
    final Configuration config =
        new EnvironmentVariables(
                "TIGER_TESTENV_SERVERS_IDP_EXTERNALJAROPTIONS_ARGUMENTS_0", "foobar")
            .and("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpError.yaml")
            .execute(
                () -> {
                  TigerGlobalConfiguration.reset();
                  return TigerGlobalConfiguration.instantiateConfigurationBean(
                          Configuration.class, "TIGER_TESTENV")
                      .get();
                });

    assertThat(config.getServers().get("idp").getExternalJarOptions().getArguments().get(0))
        .isEqualTo("foobar");
  }

  @Test
  void testPauseExecutionViaConsole() throws Exception {
    new EnvironmentVariables(
            "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersNoForwardProxy.yaml")
        .execute(
            () -> {
              TigerDirector.start();
              assertThatThrownBy(TigerDirector::pauseExecution)
                  .isInstanceOf(TigerTestEnvException.class);
              assertThat(TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedOnWorkflowUi())
                  .isFalse();
            });
  }

  @Test
  void testPauseExecutionViaWorkflowUI() {
    executeWithSecureShutdown(
        () -> {
          new EnvironmentVariables(
                  "TIGER_TESTENV_CFGFILE",
                  "src/test/resources/testdata/noServersNoForwardProxy.yaml")
              .and("fds", "fds")
              .execute(
                  () -> {
                    TigerDirector.start();
                    EnvStatusController envStatusController =
                        new EnvStatusController(
                            TigerDirector.getTigerTestEnvMgr(),
                            mock(TigerBuildPropertiesService.class));
                    TigerDirector.getLibConfig().activateWorkflowUi = true;
                    new Thread(
                            () -> {
                              TigerDirector.pauseExecution();
                              System.out.println("Execution resumes!");
                            })
                        .start();

                    envStatusController.getConfirmContinueExecution();
                    await()
                        .atMost(2000, TimeUnit.MILLISECONDS)
                        .until(
                            () ->
                                TigerDirector.getTigerTestEnvMgr()
                                    .isUserAcknowledgedOnWorkflowUi());
                  });
        });
  }

  @Test
  void testQuitTestRunViaConsole() throws Exception {
    new EnvironmentVariables(
            "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersNoForwardProxy.yaml")
        .execute(
            () -> {
              TigerDirector.start();
              TigerDirector.waitForAcknowledgedQuit();
              assertThat(TigerDirector.getTigerTestEnvMgr().isShuttingDown()).isTrue();
              assertThat(TigerDirector.getTigerTestEnvMgr().isShutDown()).isTrue();
            });
  }

  /*
   * see doc/specification/TigerTestEnvWaitForQuit.puml
   */
  @Test
  void testQuitTestRunViaWorkFlowUi() {
    TigerDirector.start();
    EnvStatusController envStatusController =
        new EnvStatusController(
            TigerDirector.getTigerTestEnvMgr(), mock(TigerBuildPropertiesService.class));
    TigerDirector.getLibConfig().activateWorkflowUi = true;

    new Thread(TigerDirector::waitForAcknowledgedQuit).start();
    envStatusController.getConfirmShutdown();
    await()
        .atMost(5000, TimeUnit.MILLISECONDS)
        .until(
            () ->
                TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedOnWorkflowUi()
                    && TigerDirector.getTigerTestEnvMgr().isShutDown());
  }

  @Test
  void fixedPortForWorkflowUi_shouldBeUsed() throws Exception {
    final int availableTcpPort = TestSocketUtils.findAvailableTcpPort();
    new EnvironmentVariables("TIGER_LIB_WORKFLOWUIPORT", Integer.toString(availableTcpPort))
        .execute(
            () -> {
              TigerDirector.start();
              final var response =
                  Unirest.get("http://localhost:" + availableTcpPort + "/").asString();
              assertThat(response.getStatus()).isEqualTo(200);
            });
  }

  private void executeWithSecureShutdown(ThrowingRunnable test) {
    executeWithSecureShutdown(test, () -> {});
  }

  private void executeWithSecureShutdown(ThrowingRunnable test, Runnable cleanup) {
    try {
      test.run();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    } finally {
      TigerDirector.getTigerTestEnvMgr().shutDown();
      cleanup.run();
    }
  }
}
