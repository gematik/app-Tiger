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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.util.InsecureTrustAllManager;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        assertThatThrownBy(TigerDirector::getProxySettings).isInstanceOf(TigerStartupException.class);
    }

    @Test
    void testDirectorSimpleIdp() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp2.yaml");
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
    }

    @SneakyThrows
    @Test
    void testDirectorDisabledProxy(final CapturedOutput capturedOutput) {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");
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

        assertThat(capturedOutput.getOut()).contains("SKIPPING TIGER PROXY settings...");
        assertThatThrownBy(con::connect).isInstanceOf(Exception.class);
    }

    @SneakyThrows
    @Test
    void testRouteHasHttpsEndpointURLConnection_certificateShouldBeVerified() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        TigerDirector.start();

        System.out.println(
            "PROXY:" + System.getProperty("http.proxyHost") + " / " + System.getProperty("https.proxyHost"));
        System.out.println(
            "PORTS:" + System.getProperty("http.proxyPort") + " / " + System.getProperty("https.proxyPort"));

        final var url = new URL("https://github.com/");

        final URLConnection con = url.openConnection();
        InsecureTrustAllManager.allowAllSsl(con);

        con.connect();
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

    @ParameterizedTest
    @CsvSource(value = {"http.proxyHost, https.proxyHost, http.proxyPort, https.proxyPort"})
    void testLocalProxyActiveSetByDefault(final String httpHost, final String httpsHost, final String httpPort,
        final String httpsPort,
        final CapturedOutput capturedOutput) {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        TigerDirector.start();

        System.out.println(
            "PROXY:" + System.getProperty(httpHost) + " / " + System.getProperty(httpsHost));
        System.out.println(
            "PORTS:" + System.getProperty(httpHost) + " / " + System.getProperty(httpsPort));

        assertThat(capturedOutput.getOut()).contains(
            "SETTING TIGER PROXY...");

        assertThat(System.getProperty(httpHost)).isNotNull();
        assertThat(System.getProperty(httpsHost)).isNotNull();
        assertThat(System.getProperty(httpPort)).isNotNull();
        assertThat(System.getProperty(httpsPort)).isNotNull();

        assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().isLocalProxyActive()).isTrue();
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
                // TODO add the mvn dependency lines to log output
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
