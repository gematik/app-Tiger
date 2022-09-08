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

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import com.github.stefanbirkner.systemlambda.Statement;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.controller.EnvStatusController;
import de.gematik.test.tiger.testenvmgr.util.InsecureTrustAllManager;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class TestTigerDirector {

    @BeforeEach
    void init() {
        TigerDirector.testUninitialize();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("TIGER_TESTENV_CFGFILE");
        TigerGlobalConfiguration.reset();
    }

    @Test
    void tigerActiveNotSet_TigerDirectorShouldThrowException() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");

        assertThat(TigerDirector.isInitialized()).isFalse();
        assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getLocalTigerProxyUrl).isInstanceOf(TigerStartupException.class);
    }

    @Test
    void testDirectorSimpleIdp() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp2.yaml");
        executeWithSecureShutdown(() -> {
            TigerDirector.start();

            assertThat(TigerDirector.isInitialized()).isTrue();
            assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
            assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy()).isNotNull();
            assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().getBaseUrl()).startsWith(
                "http://localhost");
            assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().getRbelLogger()).isNotNull();
            assertThat(
                TigerDirector.getTigerTestEnvMgr().getServers().get("idp2-simple").getConfiguration().getDockerOptions()
                    .getPorts()).hasSize(1);
            assertThat(
                TigerDirector.getTigerTestEnvMgr().getServers().get("idp2-simple").getConfiguration().getDockerOptions()
                    .getPorts().get(8080)).isNotNull();
        });
    }

    @SneakyThrows
    @Test
    void testDirectorDisabledProxy(final CapturedOutput capturedOutput) {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");
        executeWithSecureShutdown(() -> {
            try {
                TigerDirector.start();

                System.out.println(
                    "PROXY:" + System.getProperty("http.proxyHost") + " / " + System.getProperty("https.proxyHost"));
                System.out.println(
                    "PORTS:" + System.getProperty("http.proxyPort") + " / " + System.getProperty("https.proxyPort"));

                assertThat(TigerDirector.isInitialized()).isTrue();
                assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
                assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy()).isNotNull();

                final var url = new URL("http://idp-rise-tu-noproxy");

                final URLConnection con = url.openConnection();
                InsecureTrustAllManager.allowAllSsl(con);

                con.setConnectTimeout(1000);

                assertThat(capturedOutput.getOut()).contains(
                    "SKIPPING TIGER PROXY settings as localProxyActive==false...");
                assertThatThrownBy(con::connect).isInstanceOf(Exception.class);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        });

    }

    @Test
    void testRouteHasHttpsEndpointURLConnection_certificateShouldBeVerified() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        executeWithSecureShutdown(() -> {
            try {
                TigerDirector.start();

                System.out.println(
                    "PROXY:" + System.getProperty("http.proxyHost") + " / " + System.getProperty("https.proxyHost"));
                System.out.println(
                    "PORTS:" + System.getProperty("http.proxyPort") + " / " + System.getProperty("https.proxyPort"));

                final var url = new URL("https://github.com/");

                final URLConnection con;
                con = url.openConnection();
                InsecureTrustAllManager.allowAllSsl(con);

                con.connect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SneakyThrows
    @Test
    void testDirectorFalsePathToYaml() {
        final Path nonExistingPath = Paths.get("non", "existing", "file.yaml");
        System.setProperty("TIGER_TESTENV_CFGFILE", nonExistingPath.toString());
        assertThatThrownBy(TigerDirector::start)
            .isInstanceOf(TigerConfigurationException.class)
            .hasMessageContaining(nonExistingPath.toString());
    }

    @Test
    void testLocalProxyActiveSetByDefault() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        executeWithSecureShutdown(() -> {
            TigerDirector.start();
            assertThat(System.getProperty("http.proxyHost")).isNotNull();
            assertThat(System.getProperty("https.proxyHost")).isNotNull();
            assertThat(System.getProperty("http.proxyPort")).isNotNull();
            assertThat(System.getProperty("https.proxyPort")).isNotNull();
            assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().isLocalProxyActive()).isTrue();
            assertThat(SerenityRest.get("http://testExternalJar/").getStatusCode())
                .isEqualTo(200);
            assertThat(Unirest.get("http://testExternalJar/").asEmpty().getStatus())
                .isEqualTo(200);
        });
    }

    @Test
    void checkComplexKeyOverriding() throws Exception {
        final Configuration config = withEnvironmentVariable(
            "TIGER_TESTENV_SERVERS_IDP_EXTERNALJAROPTIONS_ARGUMENTS_0", "foobar")
            .and("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/idpError.yaml")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                return TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "TIGER_TESTENV")
                    .get();
            });

        assertThat(config.getServers().get("idp")
            .getExternalJarOptions()
            .getArguments().get(0))
            .isEqualTo("foobar");
    }

    @Test
    void testPauseExecutionViaConsole() throws Exception {
        withTextFromSystemIn("next\n")
            .execute(() ->
                executeWithSecureShutdown(() -> {
                    TigerDirector.start();
                    new Thread(() -> {
                        TigerDirector.pauseExecution();
                        TigerDirector.getTigerTestEnvMgr().receivedResumeTestRunExecution();
                        System.out.println("Execution resumes!");
                    }).start();

                    await().atMost(400, TimeUnit.MILLISECONDS)
                        .until(() -> TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedContinueTestRun());
                    TigerDirector.getTigerTestEnvMgr().resetUserInput();
                    assertThat(TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedContinueTestRun()).isFalse();
                }));
    }

    @Test
    void testPauseExecutionViaWorkflowUI() {
        executeWithSecureShutdown(() -> {
            TigerDirector.start();
            EnvStatusController envStatusController = new EnvStatusController(TigerDirector.getTigerTestEnvMgr());
            TigerDirector.getLibConfig().activateWorkflowUi = true;
            new Thread(() -> {
                TigerDirector.pauseExecution();
                System.out.println("Execution resumes!");
            }).start();

            envStatusController.getConfirmContinueExecution();
            await().atMost(400, TimeUnit.MILLISECONDS)
                .until(() -> TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedContinueTestRun());
        });
    }

    @Test
    void testPauseExecutionViaConsoleWrongEnter() throws Exception {
        withTextFromSystemIn("notnext\n")
            .execute(() ->
                executeWithSecureShutdown(() -> {
                    TigerDirector.start();
                    Thread thread = new Thread(TigerDirector::pauseExecution);
                    thread.start();

                    Thread.sleep(400);
                    assertThat(TigerDirector.getTigerTestEnvMgr().isUserAcknowledgedContinueTestRun())
                        .isFalse();
                    thread.interrupt();
                }));
    }

    @Test
    void testQuitTestRunViaConsole() throws Exception {
        withTextFromSystemIn("quit\n")
            .execute(() ->
                executeWithSecureShutdown(() ->
                    assertThat(catchSystemExit(() -> {
                            TigerDirector.start();
                            await().atMost(2, TimeUnit.SECONDS)
                                .until(() -> {
                                    TigerDirector.waitForQuit();
                                    return false;
                                });
                        })
                    ).isEqualTo(0)));
    }

    @Test
    void testQuitTestRunViaWorkFlowUi() throws Exception {
        withTextFromSystemIn("quit\n")
            .execute(() ->
                executeWithSecureShutdown(() ->
                    assertThat(catchSystemExit(() -> {
                        TigerDirector.start();
                        EnvStatusController envStatusController = new EnvStatusController(
                            TigerDirector.getTigerTestEnvMgr());
                        TigerDirector.getLibConfig().activateWorkflowUi = true;
                        new Thread(TigerDirector::waitForQuit).start();

                        await().atMost(4, TimeUnit.SECONDS)
                            .until(() -> {
                                envStatusController.getConfirmQuit();
                                return false;
                            });
                    })).isEqualTo(0)));
    }

    private void executeWithSecureShutdown(Statement test) {
        executeWithSecureShutdown(test, () -> {
        });
    }

    private void executeWithSecureShutdown(Statement test, Runnable cleanup) {
        try {
            test.execute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            TigerDirector.getTigerTestEnvMgr().shutDown();
            cleanup.run();
        }
    }
}
