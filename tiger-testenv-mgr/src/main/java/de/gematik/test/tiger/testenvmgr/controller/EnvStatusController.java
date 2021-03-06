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
import de.gematik.test.tiger.common.config.TigerProperties;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.data.TigerEnvStatusDto;
import de.gematik.test.tiger.testenvmgr.data.TigerServerStatusDto;
import de.gematik.test.tiger.testenvmgr.env.*;
import java.util.ArrayList;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
@Slf4j
public class EnvStatusController implements TigerUpdateListener {

    private final TigerEnvStatusDto tigerEnvStatus = new TigerEnvStatusDto();

    TigerTestEnvMgr tigerTestEnvMgr;

    private TigerProperties tigerProperties = new TigerProperties();

    public EnvStatusController(final TigerTestEnvMgr tigerTestEnvMgr) {
        this.tigerTestEnvMgr = tigerTestEnvMgr;
        this.tigerTestEnvMgr.registerNewListener(this);
    }

    @Override
    public synchronized void receiveTestEnvUpdate(final TigerStatusUpdate update) {
        log.trace("receiving update {}", update);
        try {
            receiveTestSuiteUpdate(update.getFeatureMap());

            update.getServerUpdate().forEach(this::receiveServerStatusUpdate);

            if (update.getBannerMessage() != null) {
                tigerEnvStatus.setBannerMessage(update.getBannerMessage());
                tigerEnvStatus.setBannerColor(update.getBannerColor());
                tigerEnvStatus.setBannerType(update.getBannerType());
            }
            // TODO make sure to check that the index is the expected next number, if not we do have to cache this and wait for the correct message
            //  TODO to be received and then process the cached messages in order, currently this is done on the client side
            if (update.getIndex() > tigerEnvStatus.getCurrentIndex()) {
                tigerEnvStatus.setCurrentIndex(update.getIndex());
            }
        } catch (Exception e) {
            log.error("Unable to parse update", e);
        }
    }

    private void receiveTestSuiteUpdate(Map<String, FeatureUpdate> update) {
        update.forEach((key, value) -> {
            try {
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
                            scenario.setExampleKeys(svalue.getExampleKeys());
                            scenario.setExampleList(svalue.getExampleList());
                            scenario.setVariantIndex(svalue.getVariantIndex());
                            svalue.getSteps().forEach((stkey, stvalue) -> {
                                if (scenario.getSteps().containsKey(stkey)) {
                                    StepUpdate step = scenario.getSteps().get(stkey);
                                    if (stvalue.getStatus() != TestResult.UNUSED) {
                                        step.setStatus(stvalue.getStatus());
                                    }
                                    step.setDescription(stvalue.getDescription());
                                    step.setStepIndex(stvalue.getStepIndex());
                                    if (stvalue.getRbelMetaData() != null) {
                                        if (step.getRbelMetaData() == null) {
                                            step.setRbelMetaData(new ArrayList<>());
                                        }
                                        step.getRbelMetaData().addAll(stvalue.getRbelMetaData());
                                    }
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
            } catch (Exception e) {
                log.error("Unable to parse update", e);
            }
        });
    }

    private synchronized void receiveServerStatusUpdate(final String serverName,
        final TigerServerStatusUpdate statusUpdate) {
        log.trace("Status update for server {}", serverName);
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
        log.trace("Fetch request to getStatus() received");
        if (StringUtils.isEmpty(tigerEnvStatus.getLocalProxyWebUiUrl())) {
            tigerEnvStatus.setLocalProxyWebUiUrl(
                "http://localhost:"
                    + TigerGlobalConfiguration.readString(TigerTestEnvMgr.CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT)
                    + "/webui");
        }
        tigerTestEnvMgr.setWorkflowUiSentFetch(true);
        log.trace("Sending test env status {}", tigerEnvStatus);
        return tigerEnvStatus;
    }


    @GetMapping(path = "/quit")
    public void getConfirmQuit() {
        log.trace("Fetch request to getQuit() received");
        tigerTestEnvMgr.receivedUserAcknowledgementForShutdown();
    }
    @GetMapping(path = "/continueExecution")
    public void getConfirmContinueExecution() {
        log.trace("Fetch request to continueExecution() received");
        tigerTestEnvMgr.receivedResumeTestRunExecution();
        TigerStatusUpdate update = TigerStatusUpdate.builder().bannerMessage("Resuming test run").bannerType(BannerType.MESSAGE).bannerColor("green").build();
        tigerTestEnvMgr.receiveTestEnvUpdate(update);
    }

    @GetMapping(path = "/version")
    public String getTigerVersion() {
        log.trace("Fetch requests the tiger version");
        return tigerProperties.getBuildVersion();
    }
    @GetMapping(path = "/build")
    public String getBuildDate() {
        log.trace("Fetch requests the build date");
        return tigerProperties.getBuildDate();
    }
}
