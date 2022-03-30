/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.DownloadManager;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
class EnvStatusControllerTest {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Test
    @TigerTest(tigerYaml = "")
    public void displayMessage_shouldPushToClient(final TigerTestEnvMgr envMgr) {
        final EnvStatusController envStatusController = new EnvStatusController(envMgr);

        assertThat(envStatusController.getStatus().getCurrentStatusMessage())
            .isEmpty();

        envMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder()
            .statusMessage("new status message")
            .build());

        assertThat(envStatusController.getStatus().getCurrentStatusMessage())
            .isEqualTo("new status message");
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n"
        + "  winstoneServer:\n"
        + "    type: externalJar\n"
        + "    source:\n"
        + "      - local:target/winstone.jar\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --httpPort=${free.port.0}\n"
        + "        - --webroot=.\n"
        + "      healthcheck: http://127.0.0.1:${free.port.0}\n",
        skipEnvironmentSetup = true)
    public void verifyServerStatusDuringStartup(final TigerTestEnvMgr envMgr) {
        final AtomicBoolean downloadShouldProceed = new AtomicBoolean(false);

        final DownloadManager mockDownloadManager = mock(DownloadManager.class);
        ReflectionTestUtils.setField(envMgr, "downloadManager", mockDownloadManager);
        when(mockDownloadManager.downloadJarAndReturnFile(any(), any())).thenAnswer(
            (Answer<File>) invocation -> {
                await().until(downloadShouldProceed::get);
                return new File("target/winstone.jar");
            });

        final EnvStatusController envStatusController = envMgr.getListeners().stream()
            .filter(EnvStatusController.class::isInstance)
            .map(EnvStatusController.class::cast)
            .findAny().orElseThrow();

        executorService.submit(envMgr::setUpEnvironment);

        await()
            .until(() -> envStatusController.getStatus().getServers().containsKey("winstoneServer")
                && envStatusController.getStatus().getServers().get("winstoneServer").getStatus()
                == TigerServerStatus.STARTING);

        assertThat(envStatusController.getStatus().getServers().get("winstoneServer"))
            .hasFieldOrPropertyWithValue("name", "winstoneServer")
            .hasFieldOrPropertyWithValue("status", TigerServerStatus.STARTING)
            .hasFieldOrPropertyWithValue("statusMessage", "Starting server...");

        downloadShouldProceed.set(true);

        await()
            .until(() -> envStatusController.getStatus().getServers()
                .get("winstoneServer")
                .getStatus() == TigerServerStatus.RUNNING);

        assertThat(envStatusController.getStatus().getServers().get("winstoneServer"))
            .hasFieldOrPropertyWithValue("name", "winstoneServer")
            .hasFieldOrPropertyWithValue("status", TigerServerStatus.RUNNING)
            .hasFieldOrPropertyWithValue("statusMessage", "Server winstoneServer started & running")
            .hasFieldOrPropertyWithValue("baseUrl",
                TigerGlobalConfiguration.resolvePlaceholders("http://127.0.0.1:${free.port.0}"));
    }
}