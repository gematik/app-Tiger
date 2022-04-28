/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.*;
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
    }

    @Override
    public synchronized void receiveTestEnvUpdate(final TigerStatusUpdate update) {
        log.info("receiving update " + update.getIndex());
        try {
            receiveTestSuiteUpdate(update.getFeatureMap());

            Optional.ofNullable(update.getServerUpdate())
                .map(Map::entrySet)
                .stream()
                .flatMap(Set::stream)
                .forEach(entry -> receiveServerStatusUpdate(entry.getKey(), entry.getValue()));

            if (update.getBannerMessage() != null) {
                tigerEnvStatus.setBannerMessage(update.getBannerMessage());
                tigerEnvStatus.setBannerColor(update.getBannerColor());
            }
            // TODO make sure to check that the index is the expected next number, if not we do have to cache this and wait for the correct message
            //  TODO to be received and then process the cached messages in order
            if (update.getIndex() > tigerEnvStatus.getCurrentIndex()) {
                tigerEnvStatus.setCurrentIndex(update.getIndex());
            }
        } catch (Exception e) {
            log.error("Unable to parse update", e);
        }
    }

    private void receiveTestSuiteUpdate(Map<String, FeatureUpdate> update) {
        if (update == null) {
            return;
        }
        update.forEach((key, value) -> {
            if (tigerEnvStatus.getFeatureMap().containsKey(key)) {
                FeatureUpdate feature = tigerEnvStatus.getFeatureMap().get(key);
                if (value.getStatus() != TestResult.UNUSED) {
                    feature.setStatus(value.getStatus());
                }
                feature.setDescription(value.getDescription());
                value.getScenarios().forEach((skey, svalue) -> {
                    if (feature.getScenarios().containsKey(skey)) {
                        ScenarioUpdate scenario = feature.getScenarios().get(skey);
                        if (svalue.getStatus() != TestResult.UNUSED) {
                            scenario.setStatus(svalue.getStatus());
                        }
                        scenario.setDescription(svalue.getDescription());
                        svalue.getSteps().forEach((stkey, stvalue) -> {
                            if (scenario.getSteps().containsKey(stkey)) {
                                StepUpdate step = scenario.getSteps().get(stkey);
                                if (stvalue.getStatus() != TestResult.UNUSED) {
                                    step.setStatus(stvalue.getStatus());
                                }
                                step.setDescription(stvalue.getDescription());
                            } else {
                                scenario.getSteps().put(stkey, stvalue);
                            }
                        });
                    } else {
                        feature.getScenarios().put(skey, svalue);
                    }
                });
            } else {
                tigerEnvStatus.getFeatureMap().put(key, value);
            }
        });
    }

    private synchronized void receiveServerStatusUpdate(final String serverName,
        final TigerServerStatusUpdate statusUpdate) {
        log.info("Status update for server " + serverName);
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
