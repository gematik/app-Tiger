/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
public class TestTigerTestEnvMgr {

    @BeforeAll
    public static void proxySettings() {
        // TODO check whether to remove once the Jenkinsfile has been merged to master
        if (System.getenv("PROXY_HOST") != null) {
            log.info("Applying Jenkins proxy env vars! " +
                System.getenv("PROXY_HOST") + ":" + System.getenv("PROXY_PORT"));
            System.setProperty("http.proxyHost", System.getenv("PROXY_HOST"));
            System.setProperty("http.proxyPort", System.getenv("PROXY_PORT"));
            System.setProperty("https.proxyHost", System.getenv("PROXY_HOST"));
            System.setProperty("https.proxyPort", System.getenv("PROXY_PORT"));
        }
    }

    @BeforeEach
    public void printName(TestInfo testInfo) {
        if (testInfo.getTestMethod().isPresent()) {
            log.info(Ansi.colorize("Starting " + testInfo.getTestMethod().get().getName(), RbelAnsiColors.GREEN_BOLD));
        } else {
            log.warn(Ansi.colorize("Starting UNKNOWN step", RbelAnsiColors.GREEN_BOLD));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check missing mandatory props are detected
    // check key twice in yaml leads to exception
    //

    @ParameterizedTest
    @MethodSource("cfgFileAndMandatoryPropertyProvider")
    public void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop)
        throws InvocationTargetException, IllegalAccessException {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
        nullifyObjectProperty(srv, prop);
        assertThatThrownBy(() -> envMgr.checkCfgProperties(cfgFile, srv)).isInstanceOf(TigerTestEnvException.class);
    }

    static Stream<Arguments> cfgFileAndMandatoryPropertyProvider() {
        return Stream.of(
            arguments("testDocker", "type"),
            arguments("testDocker", "source"),
            arguments("testDocker", "version"),
            arguments("testTigerProxy", "type"),
            arguments("testTigerProxy", "version"),
            arguments("testExternalJar", "type"),
            arguments("testExternalJar", "source"),
            arguments("testExternalUrl", "type"),
            arguments("testExternalUrl", "source")
        );
    }

    @Test
    public void testCheckCfgPropertiesMissingParamMandatoryServerPortProp_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testTigerProxy.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
        srv.getTigerProxyCfg().setServerPort(-1);
        assertThatThrownBy(() -> envMgr.checkCfgProperties("testTigerProxy", srv)).isInstanceOf(
            TigerTestEnvException.class);
    }

    @Test
    public void testCheckDoubleKey_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDoubleKey.yaml");

        assertThatThrownBy(TigerTestEnvMgr::new)
            .isInstanceOf(TigerConfigurationException.class)
            .hasMessageStartingWith("Duplicate key");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check minimum configurations pass the check and MVP configs are started successfully,
    // check hostname is set to key if missing
    // check for docker compose not hostname is allowed!
    //

    @ParameterizedTest
    @ValueSource(strings = {"testDocker", "testTigerProxy", "testExternalJar", "testExternalUrl"})
    public void testCheckCfgPropertiesMinimumConfigPasses_OK(String cfgFileName) {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
        envMgr.checkCfgProperties(cfgFileName, srv);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testDockerMVP", "testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
    public void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName)
        throws IOException, InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.getConfiguration().getServers().get(cfgFileName);
        try {
            envMgr.setUpEnvironment();
            Thread.sleep(2000);
        } finally {
            envMgr.shutDown(cfgFileName);
        }
    }

    @Test
    public void testHostnameIsAutosetIfMissingK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testServerWithNoHostname.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testServerWithNoHostname");
        assertThat(srv.getHostname()).isEqualTo("testServerWithNoHostname");
    }

    @Test
    public void testHostnameForDockerComposeNotAllowed_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerConfigurationException.class);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // docker details
    //

    @Test
    public void testCreateDockerNonExistingVersion() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVP.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testDockerMVP");
        srv.setVersion("200.200.200-2000");
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // externalUrl details
    //

    @Test
    public void testExternalUrlViaProxy() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
    }

    @Test
    public void testExternalUrlInternalUrl() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrlInternalServer.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.getConfiguration().getTigerProxy().setForwardToProxy(null);
        CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalUrlInternalServer");
        srv.getSource().set(0, "https://build.top.local");
        envMgr.setUpEnvironment();
    }

    @Test
    public void testCreateExternalJarEnvInvalidJar() throws IOException {
//        System.setProperty("http.nonProxyHosts", "build.top.local|*.local");
        File f = new File("WinstoneHTTPServer");
        FileUtils.deleteDirectory(f);
        if (!f.mkdirs()) {
            throw new RuntimeException("Unable to create folder '" + f.getAbsolutePath() + "'");
        }
        f = Path.of("WinstoneHTTPServer", "download").toFile();
        if (!f.createNewFile()) {
            throw new RuntimeException("Unable to create file '" + f.getAbsolutePath() + "'");
        }
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            assertThatThrownBy(envMgr::setUpEnvironment)
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Timeout waiting for external server to respond at");
        } finally {
            FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
            try {
                shutDownWebServer(envMgr, "testExternalJarMVP");
            } catch (Exception ignore) {
            }
        }
    }

    @Test
    public void testCreateExternalJarRelativePath() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
        srv.getSource().set(0, "local://miniJar.jar");
        srv.getExternalJarOptions().setWorkingDir("src/test/resources");
        srv.getExternalJarOptions().setHealthcheck("NONE");
        srv.setStartupTimeoutSec(1);
        try {
            envMgr.setUpEnvironment();
        } finally {
            shutDownWebServer(envMgr, "testExternalJarMVP");
        }
    }

    @Test
    public void testCreateExternalJarNonExistingWorkingDir() throws InterruptedException, IOException {
        File folder = new File("NonExistingFolder");
        if (folder.exists()) {
            FileUtils.deleteDirectory(folder);
        }
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
        srv.getExternalJarOptions().setWorkingDir("NonExistingFolder");
        srv.getExternalJarOptions().setHealthcheck("NONE");
        srv.setStartupTimeoutSec(1);
        try {
            envMgr.setUpEnvironment();
        } finally {
            shutDownWebServer(envMgr, "testExternalJarMVP");
            FileUtils.deleteDirectory(folder);
        }
    }

    @Test
    public void testCreateExternalJarRelativePathFileNotFound() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
        srv.getSource().set(0, "local://miniJarWHICHDOESNOTEXIST.jar");
        srv.getExternalJarOptions().setWorkingDir("src/test/resources");
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
            .hasMessageStartingWith("Local jar ").hasMessageEndingWith("miniJarWHICHDOESNOTEXIST.jar not found!");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // reverse proxy details
    //

    @Test
    public void testReverseProxy() throws IOException, InterruptedException {
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));

        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxy.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            envMgr.setUpEnvironment();
            URLConnection con = new URL("http://127.0.0.1:10020").openConnection();
            con.connect();
            String res = IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            assertThat(res).withFailMessage("Expected to receive folder index page from Winstone server")
                .startsWith("<html>").endsWith("</html>")
                .contains("Directory list generated by Winstone Servlet Engine");
            con.getInputStream().close();
        } finally {
            shutDownWebServer(envMgr, "testWinstone2");
            shutDownWebServer(envMgr, "reverseproxy1");
        }
    }

    @Test
    public void testReverseProxyManual() throws IOException, InterruptedException {
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));

        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            envMgr.setUpEnvironment();
            URLConnection con = new URL("http://127.0.0.1:10010").openConnection();
            con.connect();
            String res = IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            assertThat(res).withFailMessage("Expected to receive folder index page from Winstone server")
                .startsWith("<html>").endsWith("</html>")
                .contains("Directory list generated by Winstone Servlet Engine");
            con.getInputStream().close();
        } finally {
            shutDownWebServer(envMgr, "testWinstone3");
            shutDownWebServer(envMgr, "reverseproxy2");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // invalid general props
    //


    @Test
    public void testCreateInvalidInstanceType() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidType.yaml");
        assertThatThrownBy(() -> {
            final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            envMgr.setUpEnvironment();
        }).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateUnknownTemplate() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testUnknownTemplate.yaml");
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // helper methods


    private void shutDownWebServer(TigerTestEnvMgr envMgr, String serverName) throws InterruptedException {
        Thread.sleep(2000);
        envMgr.shutDown(serverName);
    }

    private void nullifyObjectProperty(Object obj, String propName)
        throws InvocationTargetException, IllegalAccessException {
        Method mthd = Arrays.stream(obj.getClass()
                .getMethods())
            .filter(m -> m.getName().equals("set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1)))
            .findAny().orElseThrow();
        mthd.invoke(obj, (Object) null);
    }


    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(200000);
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateDemis() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDemis.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(20000000);
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2FDV() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa-fdv.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(2000);
    }

    // TODO check pkis set, routings set,....

}
