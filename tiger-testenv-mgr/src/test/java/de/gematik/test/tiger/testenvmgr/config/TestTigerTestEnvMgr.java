/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockserver.model.HttpRequest.request;

// TGR-296 rewrite disabled tests and don't use separate java processes. if this isn't possibly, we need to change the jenkins-agent
@Slf4j
public class TestTigerTestEnvMgr {

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

    MockServer mockServer;
    MockServerClient mockServerClient;
    Expectation downloadExpectation;
    private byte[] winstoneBytes;

    @BeforeEach
    public void startServer() throws IOException {
        if (mockServer != null) {
            return;
        }
        log.info("Booting MockServer...");
        mockServer = new MockServer(SocketUtils.findAvailableTcpPorts(1).first());
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

        final File winstoneFile = new File("target/winstone.jar");
        if (!winstoneFile.exists()) {
            throw new RuntimeException("winstone.jar not found in target-folder. " +
                "Did you run mvn generate-test-resources? (It should be downloaded automatically)");
        }
        winstoneBytes = FileUtils.readFileToByteArray(winstoneFile);

        downloadExpectation = mockServerClient.when(request()
                .withPath("/download"))
            .respond(req -> HttpResponse.response()
                .withBody(winstoneBytes))[0];

        System.setProperty("mockserver.port", Integer.toString(mockServer.getLocalPort()));
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initialize();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check missing mandatory props are detected
    // check key twice in yaml leads to exception
    //
    // -----------------------------------------------------------------------------------------------------------------
    @BeforeEach
    public void printName(TestInfo testInfo) {
        TigerGlobalConfiguration.reset();
        if (testInfo.getTestMethod().isPresent()) {
            log.info(Ansi.colorize("Starting " + testInfo.getTestMethod().get().getName(), RbelAnsiColors.GREEN_BOLD));
        } else {
            log.warn(Ansi.colorize("Starting UNKNOWN step", RbelAnsiColors.GREEN_BOLD));
        }
        System.clearProperty("TIGER_TESTENV_CFGFILE");
    }

    @ParameterizedTest
    @MethodSource("cfgFileAndMandatoryPropertyProvider")
    public void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop) {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml");
        TigerGlobalConfiguration.initialize();

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
            ReflectionTestUtils.setField(srv, prop, null);
            assertThatThrownBy(() -> TigerServer.create("blub", srv, null)
                .assertThatConfigurationIsCorrect())
                .isInstanceOf(TigerTestEnvException.class);
        });
    }

    @Test
    public void testCheckCfgPropertiesMissingParamMandatoryServerPortProp_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testTigerProxy.yaml");
        TigerGlobalConfiguration.initialize();

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
            srv.getTigerProxyCfg().setServerPort(-1);
            assertThatThrownBy(() -> TigerServer.create("testTigerProxy", srv, null)
                .assertThatConfigurationIsCorrect())
                .isInstanceOf(TigerTestEnvException.class);
        });
    }

    @Test
    public void testCheckDoubleKey_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDoubleKey.yaml");
        TigerGlobalConfiguration.initialize();

        assertThatThrownBy(TigerTestEnvMgr::new)
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .hasRootCauseMessage("Duplicate keys in yaml file ('serverDouble')!");
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
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
            TigerServer.create("blub", srv, null).assertThatConfigurationIsCorrect();
        });
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"testDockerMVP", "testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
    public void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
        TigerGlobalConfiguration.initialize();
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getServers().get(cfgFileName);
            envMgr.setUpEnvironment();
        });
    }

    @Test
    public void testHostnameIsAutosetIfMissingK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testServerWithNoHostname.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            TigerServer srv = envMgr.getServers().get("testServerWithNoHostname");
            assertThat(srv.getHostname()).isEqualTo("testServerWithNoHostname");
        });
    }

    @Test
    public void testHostnameForDockerComposeNotAllowed_NOK() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerConfigurationException.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // docker details
    //

    @Test
    public void testCreateDockerNonExistingVersion() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVP.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testDockerMVP");
            srv.setVersion("200.200.200-2000");
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // externalUrl details
    //

    @Disabled
    @Test
    public void testExternalUrlViaProxy() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> envMgr.setUpEnvironment());
    }


    @ParameterizedTest
    @ValueSource(strings = {"withPkiKeys", "withUrlMappings"})
    public void testExternalUrl_withDetails(String cfgFileName) {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl_" + cfgFileName + ".yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> envMgr.setUpEnvironment());
    }

    @Test
    public void testExternalUrlInternalUrl() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrlInternalServer.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getTigerProxy().setForwardToProxy(null);
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalUrlInternalServer");
            srv.getSource().set(0, "https://build.top.local");
            envMgr.setUpEnvironment();
        });
    }

    @Test
    public void testCreateExternalJarRelativePath() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
            srv.getSource().set(0, "local://winstone.jar");
            srv.getExternalJarOptions().setWorkingDir("target/");
            srv.getExternalJarOptions().setHealthcheck("NONE");
            srv.setStartupTimeoutSec(1);
            envMgr.setUpEnvironment();
        });
    }

    @Disabled
    @Test
    public void testCreateExternalJarNonExistingWorkingDir() throws IOException {
        File folder = new File("NonExistingFolder");
        if (folder.exists()) {
            FileUtils.deleteDirectory(folder);
        }
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
            srv.getExternalJarOptions().setWorkingDir("NonExistingFolder");
            srv.getExternalJarOptions().setHealthcheck("NONE");
            srv.setStartupTimeoutSec(1);
            try {
                envMgr.setUpEnvironment();
            } finally {
                FileUtils.forceDeleteOnExit(folder);
            }
        });
    }

    @Test
    public void workingDirNotSet_ShouldDefaultToOsTempDirectory() {
        final Integer port = SocketUtils.findAvailableTcpPorts(1).first();
        String yamlSource = "testenv:\n" +
            "   cfgfile: src/test/resources/tiger-testenv.yaml\n" +
            "servers:\n" +
            "  externalJarServer:\n" +
                "    type: externalJar\n" +
                "    source:\n" +
                "      - \"http://localhost:${mockserver.port}/download\"\n" +
                "    externalJarOptions:\n" +
                "      healthcheck: http://127.0.0.1:" + port + "\n" +
                "      arguments:\n" +
                "        - \"--httpPort=" + port + "\"\n" +
                "        - \"--webroot=.\"\n";

        TigerGlobalConfiguration.readFromYaml(yamlSource, "tiger");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            final String workingDir = envMgr.getServers().get("externalJarServer")
                .getConfiguration().getExternalJarOptions().getWorkingDir();
            assertThat(new File(workingDir))
                .exists()
                .isDirectoryContaining(file -> file.getName().equals("download"))
                .isDirectoryContaining(file -> file.getName().equals("download.dwnProps"));
        });
    }

    @Test
    public void testCreateExternalJarRelativePathFileNotFound() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
            srv.getSource().set(0, "local://miniJarWHICHDOESNOTEXIST.jar");
            srv.getExternalJarOptions().setWorkingDir("src/test/resources");
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Local jar ").hasMessageEndingWith("miniJarWHICHDOESNOTEXIST.jar not found!");
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // reverse proxy details
    //

    @Disabled
    @Test
    public void testReverseProxy() throws IOException, InterruptedException {
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));

        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxy.yaml");
        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            URLConnection con = new URL("http://127.0.0.1:10020").openConnection();
            con.connect();
            String res = IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            assertThat(res).withFailMessage("Expected to receive folder index page from Winstone server")
                .startsWith("<html>").endsWith("</html>")
                .contains("Directory list generated by Winstone Servlet Engine");
            con.getInputStream().close();
        });
    }

    @Disabled
    @Test
    public void testReverseProxyManual() throws IOException {
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));

        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            URLConnection con = new URL("http://127.0.0.1:10010").openConnection();
            con.connect();
            String res = IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            assertThat(res).withFailMessage("Expected to receive folder index page from Winstone server")
                .startsWith("<html>").endsWith("</html>")
                .contains("Directory list generated by Winstone Servlet Engine");
            con.getInputStream().close();
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // invalid general props
    //

    @Test
    public void testCreateInvalidInstanceType() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidType.yaml");
        TigerGlobalConfiguration.initialize();
        assertThatThrownBy(() -> {
            final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            envMgr.setUpEnvironment();
        }).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateUnknownTemplate() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testUnknownTemplate.yaml");
        TigerGlobalConfiguration.initialize();
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateInvalidPkiKeys_wrongType() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_wrongType.yaml");
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateInvalidPkiKeys_missingCertificate() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_missingCertificate.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testCreateInvalidPkiKeys_emptyCertificate() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_emptyCertificate.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testInvalidUrlMappings_noArrow() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidUrlMappings_noArrow.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("The urlMappings configuration 'https://bla' is not correct. Please check your .yaml-file.");
    }

    @Test
    public void testInvalidUrlMappings_noDestinationRoute() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidUrlMappings_noDestinationRoute.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("The urlMappings configuration 'https://bla -->' is not correct. Please check your .yaml-file.");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // helper methods

    private void createTestEnvMgrSafelyAndExecute(ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
        TigerTestEnvMgr envMgr = null;
        try {
            TigerGlobalConfiguration.initialize();
            envMgr = new TigerTestEnvMgr();
            testEnvMgrConsumer.accept(envMgr);
        } finally {
            if (envMgr != null) {
                envMgr.shutDown();
            }
        }
    }

    private void shutDownWebServer(TigerTestEnvMgr envMgr) throws InterruptedException {
        Thread.sleep(2000);
        envMgr.shutDown();
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            try {
                Thread.sleep(200000);
            } catch (InterruptedException e) {
            }
        });
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateDemis() {
        System.setProperty("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDemis.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            try {
                Thread.sleep(20000000);
            } catch (InterruptedException e) {
            }
        });
    }

    @Test
    @Disabled("Only for local testing as CI tests would take too long for this test method")
    public void testCreateEpa2FDV() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa-fdv.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(2000);
    }
}
