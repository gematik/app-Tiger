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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
@Slf4j
public class EnvStatusController implements TigerUpdateListener {

    private final TigerEnvStatusDto tigerEnvStatus = new TigerEnvStatusDto();

    public EnvStatusController(final TigerTestEnvMgr tigerTestEnvMgr) {
        tigerTestEnvMgr.registerNewListener(this);
        if (!TigerGlobalConfiguration.readBoolean("tiger.skipEnvironmentSetup", false)) {
            log.info("Starting Test-Env setup");
            tigerTestEnvMgr.setUpEnvironment();
        }
    }

    @Override
    public void receiveTestEnvUpdate(final TigerStatusUpdate update) {
        tigerEnvStatus.setCurrentStatusMessage(update.getStatusMessage());
        Optional.ofNullable(update.getServerUpdate())
            .map(Map::entrySet)
            .stream()
            .flatMap(Set::stream)
            .forEach(entry -> receiveServerStatusUpdate(entry.getKey(), entry.getValue()));
    }

    private synchronized void receiveServerStatusUpdate(final String serverName,
        final TigerServerStatusUpdate statusUpdate) {
        final TigerServerStatusDto serverStatus = tigerEnvStatus.getServers()
            .getOrDefault(serverName, new TigerServerStatusDto());
        serverStatus.setName(serverName);
        if (statusUpdate.getStatus() != null) {
            serverStatus.setStatus(statusUpdate.getStatus());
        }
        if (statusUpdate.getType() != null) {
            serverStatus.setType(statusUpdate.getType());
        }
        if (statusUpdate.getBaseUrl() != null) {
            serverStatus.setBaseUrl(statusUpdate.getBaseUrl());
        }
        if (statusUpdate.getStatusMessage() != null) {
            serverStatus.setStatusMessage(statusUpdate.getStatusMessage());
            serverStatus.getStatusUpdates().add(statusUpdate.getStatusMessage());
        }
        tigerEnvStatus.getServers().put(serverName, serverStatus);
    }

    
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public TigerEnvStatusDto getStatus() {
        return tigerEnvStatus;
    }
}
