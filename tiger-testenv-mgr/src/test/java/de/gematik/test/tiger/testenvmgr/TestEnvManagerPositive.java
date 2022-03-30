/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
@Getter
public class TestEnvManagerPositive extends AbstractTestTigerTestEnvMgr {

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check minimum configurations pass the check and MVP configs are started successfully,
    // check hostname is set to key if missing
    // check for docker compose not hostname is allowed!
    //

    @ParameterizedTest
    @ValueSource(strings = {"testDocker", "testTigerProxy", "testExternalJar", "testExternalUrl"})
    public void testCheckCfgPropertiesMinimumConfigPasses_OK(String cfgFileName) {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"));

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
            TigerServer.create("blub", srv, mockTestEnvMgr()).assertThatConfigurationIsCorrect();
        });
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"testDockerMVP", "testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
    public void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"));

        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getServers().get(cfgFileName);
            envMgr.setUpEnvironment();
        });
    }

    @Test
    public void testHostnameIsAutosetIfMissingK() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testServerWithNoHostname.yaml"));

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            TigerServer srv = envMgr.getServers().get("testServerWithNoHostname");
            assertThat(srv.getHostname()).isEqualTo("testServerWithNoHostname");
        });
    }

    @Test
    public void testHostnameForDockerComposeNotAllowed_NOK() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml"));

        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerConfigurationException.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // docker details
    //

    @Test
    public void testCreateDockerNonExistingVersion() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
                "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVP.yaml",
            "tiger.servers.testDockerMVP.version", "200.200.200-2000"));
        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // externalUrl details
    //

    @Test
    @Disabled("TGR-296 tests fail due to proxy on jenkins. issue seems to be ((HttpsURLConnection) con).usingProxy() == false")
    public void testExternalUrlViaProxy() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml"));
        createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"withPkiKeys", "withUrlMappings"})
    public void testExternalUrl_withDetails(String cfgFileName) {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl_" + cfgFileName + ".yaml"));
        createTestEnvMgrSafelyAndExecute(envMgr -> envMgr.setUpEnvironment());
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrlInternalServer.yaml",
        skipEnvironmentSetup = true)
    public void testExternalUrlInternalUrl(TigerTestEnvMgr envMgr) {
        envMgr.getConfiguration().getTigerProxy().setForwardToProxy(null);
        CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalUrlInternalServer");
        srv.getSource().set(0, "https://build.top.local");
        envMgr.setUpEnvironment();
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testExternalJarMVP:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:winstone.jar\n"
        + "    externalJarOptions:\n"
        + "      workingDir: 'target/'\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n"
        + "      healthcheck: http://127.0.0.1:${free.port.0}\n",
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathWithWorkingDir(TigerTestEnvMgr envMgr) {
        envMgr.setUpEnvironment();
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testExternalJarMVP:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:target/winstone.jar\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n"
        + "      healthcheck: http://127.0.0.1:${free.port.0}\n",
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathWithoutWorkingDir(TigerTestEnvMgr envMgr) {
        envMgr.setUpEnvironment();
    }

    @Disabled
    @Test
    public void testCreateExternalJarNonExistingWorkingDir() throws IOException {
        File folder = new File("NonExistingFolder");
        if (folder.exists()) {
            FileUtils.deleteDirectory(folder);
        }
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");

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
    @TigerTest(tigerYaml = "servers:\n" +
        "  externalJarServer:\n" +
        "    type: externalJar\n" +
        "    source:\n" +
        "      - \"http://localhost:${mockserver.port}/download\"\n" +
        "    externalJarOptions:\n" +
        "      healthcheck: http://127.0.0.1:${free.port.0}\n" +
        "      arguments:\n" +
        "        - \"--httpPort=${free.port.0}\"\n" +
        "        - \"--webroot=.\"\n")
    public void workingDirNotSet_ShouldDefaultToOsTempDirectory(TigerTestEnvMgr envMgr) {
        final String workingDir = envMgr.getServers().get("externalJarServer")
            .getConfiguration().getExternalJarOptions().getWorkingDir();
        assertThat(new File(workingDir))
            .exists()
            .isDirectoryContaining(file -> file.getName().equals("download"))
            .isDirectoryContaining(file -> file.getName().equals("download.dwnProps"));
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml",
        additionalProperties = {"tiger.servers.testExternalJarMVP.source.0=local://miniJarWHICHDOESNOTEXIST.jar",
            "tiger.servers.testExternalJarMVP.externalJarOptions.workingDir=src/test/resources"},
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathFileNotFound(TigerTestEnvMgr envMgr) {
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
            .hasMessageStartingWith("Local jar ").hasMessageEndingWith("miniJarWHICHDOESNOTEXIST.jar not found!");
    }
}
