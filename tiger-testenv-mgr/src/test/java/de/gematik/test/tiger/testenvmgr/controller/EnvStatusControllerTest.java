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

package de.gematik.test.tiger.testenvmgr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.*;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.io.File;
import java.util.Map;
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

        assertThat(envStatusController.getStatus().getFeatureMap()).isNull();

        envMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder()
            .featureMap(Map.of("feature", FeatureUpdate.builder()
                    .description("feature")
                    .scenarios(Map.of(
                        "scenario", ScenarioUpdate.builder().description("scenario")
                            .steps(Map.of("step", StepUpdate.builder().description("step").build()
                            )).build()
                    )).build()
            )).build());

        assertThat(envStatusController.getStatus().getFeatureMap().get("feature").getDescription()).isEqualTo("feature");
        assertThat(envStatusController.getStatus().getFeatureMap().get("feature").getScenarios().get("scenario").getDescription()).isEqualTo("scenario");
        assertThat(envStatusController.getStatus().getFeatureMap().get("feature").getScenarios().get("scenario").getSteps().get("step").getDescription()).isEqualTo("step");
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
