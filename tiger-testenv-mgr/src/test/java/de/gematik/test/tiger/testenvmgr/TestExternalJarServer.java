/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
class TestExternalJarServer extends AbstractTestTigerTestEnvMgr {
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

    void testCreateExternalJarRelativePathWithWorkingDir(TigerTestEnvMgr envMgr) {
        assertThatNoException().isThrownBy(
                () -> executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr)
        );
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
    void testCreateExternalJarRelativePathWithoutWorkingDir(TigerTestEnvMgr envMgr) {
        assertThatNoException().isThrownBy(
                () -> executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr))
        ;
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
        + "      workingDir: '.'\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n",
        skipEnvironmentSetup = true)
    void testCreateExternalJarRelativePathWithRelativeWorkingDir(TigerTestEnvMgr envMgr) {
        assertThatNoException().isThrownBy(
                ()-> {
                    executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr);
                }
        );
    }

    @Test
    void testCreateExternalJarNonExistingWorkingDir() throws IOException {
        File folder = new File("NonExistingFolder");
        if (folder.exists()) {
            FileUtils.deleteDirectory(folder);
        }

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
            srv.getExternalJarOptions().setWorkingDir("NonExistingFolder");
            srv.setHealthcheckUrl("NONE");
            srv.setStartupTimeoutSec(1);
            try {assertThatNoException().isThrownBy(envMgr::setUpEnvironment);

            } finally {
                FileUtils.forceDeleteOnExit(folder);
            }
        }, "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
    }

    @Test
    @TigerTest(cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml",
        additionalProperties = {"tiger.servers.testExternalJarMVP.source.0=local:miniJarWHICHDOESNOTEXIST.jar",
            "tiger.servers.testExternalJarMVP.externalJarOptions.workingDir=src/test/resources"},
        skipEnvironmentSetup = true)
    void testCreateExternalJarRelativePathFileNotFound(TigerTestEnvMgr envMgr) {
        executeWithSecureShutdown(() -> {
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class)
                .hasMessageStartingWith("Local jar-file")
                .hasMessageContaining("miniJarWHICHDOESNOTEXIST.jar")
                .hasMessageEndingWith(" not found!");
        }, envMgr);
    }

    private void executeWithSecureShutdown(Runnable test, TigerTestEnvMgr envMgr) {
        try {
            test.run();
        } finally {
            envMgr.shutDown();
        }
    }
}
