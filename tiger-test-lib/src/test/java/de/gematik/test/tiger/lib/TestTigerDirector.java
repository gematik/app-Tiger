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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.testenvmgr.InsecureTrustAllManager;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
public class TestTigerDirector {

    @BeforeEach
    public void init() {
        TigerDirector.testUninitialize();
    }

    @AfterEach
    public void clearProperties() {
        System.clearProperty("TIGER_TESTENV_CFGFILE");
        TigerGlobalConfiguration.reset();
    }

    @Test
    public void tigerActiveNotSet_TigerDirectorShouldThrowException() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp.yaml");

        assertThat(TigerDirector.isInitialized()).isFalse();
        assertThatThrownBy(TigerDirector::getTigerTestEnvMgr).isInstanceOf(TigerStartupException.class);
        assertThatThrownBy(TigerDirector::getProxySettings).isInstanceOf(TigerStartupException.class);
    }

    @Test
    public void testDirectorSimpleIdp() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/simpleIdp2.yaml");
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy();

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
    public void testDirectorDisabledProxy(CapturedOutput capturedOutput) {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyDisabled.yaml");
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy();

        System.out.println(
            "PROXY:" + System.getProperty("http.proxyHost") + " / " + System.getProperty("https.proxyHost"));
        System.out.println(
            "PORTS:" + System.getProperty("http.proxyPort") + " / " + System.getProperty("https.proxyPort"));

        assertThat(TigerDirector.isInitialized()).isTrue();
        assertThat(TigerDirector.getTigerTestEnvMgr()).isNotNull();
        assertThat(TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy()).isNotNull();

        var url = new URL("http://idp-rise-tu-noproxy");

        URLConnection con = url.openConnection();
        InsecureTrustAllManager.allowAllSsl(con);

        con.setConnectTimeout(1000);

        assertThat(capturedOutput.getOut()).contains("SKIPPING TIGER PROXY settings...");
        assertThatThrownBy(con::connect).isInstanceOf(Exception.class);
    }

    @SneakyThrows
    @Test
    public void testRouteHasHttpsEndpointURLConnection_certificateShouldBeVerified() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy();

        System.out.println(
            "PROXY:" + System.getProperty("http.proxyHost") + " / " + System.getProperty("https.proxyHost"));
        System.out.println(
            "PORTS:" + System.getProperty("http.proxyPort") + " / " + System.getProperty("https.proxyPort"));

        var url = new URL("https://github.com/");

        URLConnection con = url.openConnection();
        InsecureTrustAllManager.allowAllSsl(con);

        con.connect();
    }

    @SneakyThrows
    @Test
    public void testDirectorFalsePathToYaml() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "non/existing/file.yaml");
        assertThatThrownBy(TigerDirector::startMonitorUITestEnvMgrAndTigerProxy)
            .isInstanceOf(TigerEnvironmentStartupException.class)
            .hasMessageContaining("non/existing/file.yaml");
    }

    @ParameterizedTest
    @CsvSource(value = {"http.proxyHost, https.proxyHost, http.proxyPort, https.proxyPort"})
    public void testLocalProxyActiveSetByDefault(String httpHost, String httpsHost, String httpPort, String httpsPort,
                                                 CapturedOutput capturedOutput) {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/proxyEnabled.yaml");
        TigerDirector.startMonitorUITestEnvMgrAndTigerProxy();

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
    public void checkComplexKeyOverriding() throws Exception {
        final Configuration config = withEnvironmentVariable(
            "TIGER_TESTENV_SERVERS_IDP_EXTERNALJAROPTIONS_ARGUMENTS_0", "foobar")
            .execute(() -> {
                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readFromYaml(FileUtils.readFileToString(new File("src/test/resources/testdata/idpError.yaml")));
                return TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "TIGER_TESTENV");
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
