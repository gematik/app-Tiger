/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import net.serenitybdd.rest.SerenityRest;
import org.awaitility.core.ConditionTimeoutException;
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
            // TODO TGR-124 upgrading to testcontainer 1.16.0 causes the ports info
            // to be not available in docker config network bindings
            // so make sure we get ONE valid value here!
            // see https://github.com/testcontainers/testcontainers-java/issues/4489
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
                    TigerDirector.getTigerTestEnvMgr().resetUserAcknowledgedContinueTestRun();
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

    // TODO TGR-253 create test cases for polarion sync and afo reporter,  rethink architecture and make it pluggable
/*
    public static void synchronizeTestCasesWithPolarion() {
        if (!checkIsInitialized()) {
            return;
        }

        if (OSEnvironment.getAsBoolean("TIGER_SYNC_TESTCASES")) {
            try {
                Method polarionToolBoxMain = Class.forName("de.gematik.polarion.toolbox.ToolBox")
                    .getDeclaredMethod("main", String[].class);
                String[] args = new String[]{"-m", "tcimp", "-dryrun"};
                // TODO read from tiger-testlib.yaml or env vars values for -h -u -p -prj -aq -fd -f -bdd

                log.info("Syncing test cases with Polarion...");
                polarionToolBoxMain.invoke(null, (Object[]) args);
                log.info("Test cases synched with Polarion...");
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new TigerLibraryException("Unable to access Polarion Toolbox! "
                    + "Be sure to have it included in mvn dependencies.", e);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new TigerLibraryException("Unable to call Polarion Toolbox's main method!", e);
            }
        }
    }

    public static void beforeTestThreadStart() {
        if (!checkIsInitialized()) {
            return;
        }
        if (proxiesMap.containsKey(tid())) {
            log.warn("Proxy for given thread '" + tid() + "' already initialized!");
            return;
        }
        // instantiate proxy and supply routes and register message provider as listener to proxy
        final var threadProxy = new TigerProxy(tigerTestEnvMgr.getConfiguration().getTigerProxy());
        getTigerTestEnvMgr().getRoutes().forEach(route -> threadProxy.addRoute(route[0], route[1]));
        threadProxy.addRbelMessageListener(rbelMsgProviderMap.computeIfAbsent(tid(), key -> new RbelMessageProvider()));
        proxiesMap.putIfAbsent(tid(), threadProxy);
    }

    public static void createAfoRepoort() {
        if (!checkIsInitialized()) {
            return;
        }
        // TODO create Aforeport and embedd it into serenity report
    }

    private static boolean checkIsInitialized() {
        if (!initialized) {
            throw new AssertionError("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
        }
        return initialized;
    }
}
     */
}
