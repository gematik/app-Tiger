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

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.File;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.SocketUtils;

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
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
            TigerServer.create("blub", srv, null).assertThatConfigurationIsCorrect();
        });
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(strings = {"testDockerMVP", "testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
    public void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");

        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getServers().get(cfgFileName);
            envMgr.setUpEnvironment();
        });
    }

    @Test
    public void testHostnameIsAutosetIfMissingK() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testServerWithNoHostname.yaml");

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            TigerServer srv = envMgr.getServers().get("testServerWithNoHostname");
            assertThat(srv.getHostname()).isEqualTo("testServerWithNoHostname");
        });
    }

    @Test
    public void testHostnameForDockerComposeNotAllowed_NOK() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml");

        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerConfigurationException.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // docker details
    //

    @Test
    public void testCreateDockerNonExistingVersion() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVP.yaml");
        TigerGlobalConfiguration.putValue("tiger.servers.testDockerMVP.version",
            "200.200.200-2000");
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
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> envMgr.setUpEnvironment());
    }


    @ParameterizedTest
    @ValueSource(strings = {"withPkiKeys", "withUrlMappings"})
    public void testExternalUrl_withDetails(String cfgFileName) {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl_" + cfgFileName + ".yaml");
        createTestEnvMgrSafelyAndExecute(envMgr -> envMgr.setUpEnvironment());
    }

    @Test
    public void testExternalUrlInternalUrl() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrlInternalServer.yaml");

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getTigerProxy().setForwardToProxy(null);
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalUrlInternalServer");
            srv.getSource().set(0, "https://build.top.local");
            envMgr.setUpEnvironment();
        });
    }

    @Test
    public void testCreateExternalJarRelativePath() {
        TigerGlobalConfiguration.readFromYaml("servers:\n"
            + "  testExternalJarMVP:\n"
            + "    hostname: testExternalJarMVP\n"
            + "    type: externalJar\n"
            + "    source:\n"
            + "      - local://download\n"
            + "    workingDir: 'target/'\n"
            + "    externalJarOptions:\n"
            + "      arguments:\n"
            + "        - --httpPort=${free.port.0}\n"
            + "        - --webroot=.\n"
            + "      healthcheck: http://127.0.0.1:${free.port.0}\n", "tiger");

        createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
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
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
        TigerGlobalConfiguration.putValue("tiger.servers.testExternalJarMVP.source.0",
            "local://miniJarWHICHDOESNOTEXIST.jar");
        TigerGlobalConfiguration.putValue("tiger.servers.testExternalJarMVP.externalJarOptions.workingDir",
            "src/test/resources");

        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Local jar ").hasMessageEndingWith("miniJarWHICHDOESNOTEXIST.jar not found!"));
    }

}
