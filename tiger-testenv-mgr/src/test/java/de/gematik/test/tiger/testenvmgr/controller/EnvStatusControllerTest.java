/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.DownloadManager;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

@Slf4j
class EnvStatusControllerTest {

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Test
    @TigerTest(tigerYaml = "")
    public void displayMessage_shouldPushToClient(TigerTestEnvMgr envMgr) {
        EnvStatusController envStatusController = new EnvStatusController(envMgr);

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
    public void verifyServerStatusDuringStartup(TigerTestEnvMgr envMgr) {
        AtomicBoolean downloadShouldProceed = new AtomicBoolean(false);

        final DownloadManager mockDownloadManager = mock(DownloadManager.class);
        ReflectionTestUtils.setField(envMgr, "downloadManager", mockDownloadManager);
        when(mockDownloadManager.downloadJarAndReturnFile(any(), any())).thenAnswer(
            (Answer<File>) invocation -> {
                await()
                    .until(downloadShouldProceed::get);
                return new File("target/winstone.jar");
            });

        EnvStatusController envStatusController = envMgr.getListeners().stream()
            .filter(EnvStatusController.class::isInstance)
            .map(EnvStatusController.class::cast)
            .findAny().orElseThrow();

        executorService.submit(envMgr::setUpEnvironment);

        assertThat(envStatusController.getStatus().getServers())
            .extractingByKey("winstoneServer")
            .isEqualTo(TigerServerStatusDto.builder()
                .name("winstoneServer")
                .status(TigerServerStatus.STARTING)
                .statusMessage("Starting server...")
                .build());

        downloadShouldProceed.set(true);

        await()
            .until(() -> envStatusController.getStatus().getServers()
                .get("winstoneServer")
                .getStatus() == TigerServerStatus.RUNNING);
        assertThat(envStatusController.getStatus().getServers()
            .get("winstoneServer")
            .getStatusMessage())
            .isEqualTo("Server started & running");
    }
}