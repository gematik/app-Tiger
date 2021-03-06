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
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;

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
        log.info("Starting testCheckCfgPropertiesMinimumConfigPasses_OK for {}", cfgFileName);
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"));

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
            TigerServer.create("blub", srv, mockTestEnvMgr()).assertThatConfigurationIsCorrect();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"testComposeMVP", "testDockerMVP", "testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
    public void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
        log.info("Starting testSetUpEnvironmentNShutDownMinimumConfigPasses_OK for {}", cfgFileName);
        if (cfgFileName.equals("testDockerMVP")) {
            log.info("Active Docker containers: \n{}",
                DockerClientFactory.instance().client().listContainersCmd().exec().stream()
                    .map(container -> String.join(", ", container.getNames()) + " -> " + container.toString())
                    .collect(Collectors.joining("\n")));
        }
        FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.getConfiguration().getServers().get(cfgFileName);
            envMgr.setUpEnvironment();
        }, "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
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

        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // docker details
    //

    @Test
    public void testCreateDockerNonExistingVersion() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVPNonExistingVersion.yaml"));
        createTestEnvMgrSafelyAndExecute(envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class));
    }

    @Test
    public void testCreateDockerComposeAndCheckPortIsAvailable() throws IOException {
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();
            String host = System.getenv("DOCKER_HOST");
            if (host == null) {
                host = "localhost";
            } else {
                host = new URI(host).getHost();
            }
            log.info("Web server expected to serve at {}",
                TigerGlobalConfiguration.resolvePlaceholders("http://" + host + ":${free.port.1}"));
            try {
                log.info("Web server responds with: " +
                    Unirest.spawnInstance()
                        .get(TigerGlobalConfiguration.resolvePlaceholders("http://" + host + ":${free.port.1}"))
                        .asString().getBody());
            } catch (Exception e) {
                log.error("Unable to retrieve document from docker compose webserver...", e);
            }
            assertThat(Unirest.spawnInstance().get(
                    TigerGlobalConfiguration.resolvePlaceholders("http://" + host + ":${free.port.1}"))
                .asString().getStatus()).isEqualTo(200);
        }, "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeMVP.yaml");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // externalUrl details
    //

    @Test
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
        createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrlInternalServer.yaml",
        skipEnvironmentSetup = true)
    public void testExternalUrlInternalUrl(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(() -> {
            envMgr.getConfiguration().getTigerProxy().setForwardToProxy(null);
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalUrlInternalServer");
            srv.getSource().set(0, "https://build.top.local");
            envMgr.setUpEnvironment();
        }, envMgr);
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testExternalJarMVP:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:winstone.jar\n"
        + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
        + "    healthcheckReturnCode: 200\n"
        + "    externalJarOptions:\n"
        + "      workingDir: 'target/'\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n",
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathWithWorkingDir(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr);
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  testExternalJarMVP:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:target/winstone.jar\n"
        + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
        + "    healthcheckReturnCode: 200\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n",
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathWithoutWorkingDir(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr);
    }

    @Test
    public void testCreateExternalJarNonExistingWorkingDir() throws IOException {
        File folder = new File("NonExistingFolder");
        if (folder.exists()) {
            FileUtils.deleteDirectory(folder);
        }

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
            srv.getExternalJarOptions().setWorkingDir("NonExistingFolder");
            srv.setHealthcheckUrl("NONE");
            srv.setStartupTimeoutSec(1);
            try {
                envMgr.setUpEnvironment();
            } finally {
                FileUtils.forceDeleteOnExit(folder);
            }
        }, "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n" +
        "  externalJarServer:\n" +
        "    type: externalJar\n" +
        "    source:\n" +
        "      - \"http://localhost:${mockserver.port}/download\"\n" +
        "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n" +
        "    healthcheckReturnCode: 200\n" +
        "    externalJarOptions:\n" +
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
    @TigerTest(tigerYaml = "servers:\n"
        + "  externalJarServer:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - \"http://localhost:${mockserver.port}/download\"\n"
        + "    healthcheckUrl: http://127.0.0.1:${free.port.0}/foo/bar/wrong/url\n"
        + "    healthcheckReturnCode: 200\n"
        + "    startupTimeoutSec: 1\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - \"--httpPort=${free.port.0}\"\n"
        + "        - \"--webroot=.\"\n", skipEnvironmentSetup = true)
    public void healthcheckEndpointGives404AndExpecting200_environmentShouldNotStartUp(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(() -> {
            assertThatThrownBy(() -> envMgr.setUpEnvironment())
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessageContaining("/foo/bar/wrong/url")
                .hasMessageContaining("Timeout");
        }, envMgr);
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml",
        additionalProperties = {"tiger.servers.testExternalJarMVP.source.0=local://miniJarWHICHDOESNOTEXIST.jar",
            "tiger.servers.testExternalJarMVP.externalJarOptions.workingDir=src/test/resources"},
        skipEnvironmentSetup = true)
    public void testCreateExternalJarRelativePathFileNotFound(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(() -> {
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Local jar ").hasMessageEndingWith("miniJarWHICHDOESNOTEXIST.jar not found!");
        }, envMgr);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // local tiger proxy details
    //

    @Test
    @TigerTest(tigerYaml = "servers:\n" +
        "  externalJarServer:\n" +
        "    type: externalJar\n" +
        "    source:\n" +
        "      - \"http://localhost:${mockserver.port}/download\"\n" +
        "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n" +
        "    healthcheckReturnCode: 200\n" +
        "    externalJarOptions:\n" +
        "      arguments:\n" +
        "        - \"--httpPort=${free.port.0}\"\n" +
        "        - \"--webroot=.\"\n",
        skipEnvironmentSetup = true)
    public void startLocalTigerProxyAndCheckPropertiesSet(TigerTestEnvMgr envMgr) {
        assertThat(TigerGlobalConfiguration.readIntegerOptional(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT).get()).isBetween(0,
            655536);
        assertThat(TigerGlobalConfiguration.readIntegerOptional(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT).get()).isBetween(0,
            655536);
    }

    @Test
    @TigerTest(tigerYaml = "tigerProxy:\n" +
        "  proxyPort: ${free.port.1}\n" +
        "  adminPort: ${free.port.2}\n" +
        "servers:\n" +
        "  externalJarServer:\n" +
        "    type: externalJar\n" +
        "    source:\n" +
        "      - \"http://localhost:${mockserver.port}/download\"\n" +
        "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n" +
        "    healthcheckReturnCode: 200\n" +
        "    externalJarOptions:\n" +
        "      arguments:\n" +
        "        - \"--httpPort=${free.port.0}\"\n" +
        "        - \"--webroot=.\"\n",
        skipEnvironmentSetup = true)
    public void startLocalTigerProxyWithConfiguredPortsAndCheckPropertiesMatch(TigerTestEnvMgr envMgr) {
        assertThat(TigerGlobalConfiguration.readIntegerOptional(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT).get()).isEqualTo(
            TigerGlobalConfiguration.readIntegerOptional("free.port.2").get());
        assertThat(TigerGlobalConfiguration.readIntegerOptional(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT).get()).isEqualTo(
            TigerGlobalConfiguration.readIntegerOptional("free.port.1").get());
    }

    private void executeWithSecureShutdown(Runnable test, TigerTestEnvMgr envMgr) {
        try {
            test.run();
        } finally {
            envMgr.shutDown();
        }
    }


}
