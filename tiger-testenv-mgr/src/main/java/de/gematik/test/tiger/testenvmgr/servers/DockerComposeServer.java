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

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.data.config.CfgDockerOptions;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

public class DockerComposeServer extends TigerServer {

    @Builder
    DockerComposeServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super("", serverId, tigerTestEnvMgr, configuration);
        if (!StringUtils.isBlank(configuration.getHostname())) {
            throw new TigerConfigurationException("Hostname property is not supported for docker compose nodes!");
        }
    }

    @Override
    public void performStartup() {
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.DOCKER_COMPOSE)
            .build());

        statusMessage("Starting docker compose for " + getServerId() + " from " + getDockerSource());
        getTigerTestEnvMgr().getDockerManager().startComposition(this);
        statusMessage("Docker compose " + getServerId() + " started");
    }

    public String getDockerSource() {
        return getConfiguration().getSource().get(0);
    }

    public CfgDockerOptions getDockerOptions() {
        return getConfiguration().getDockerOptions();
    }

    public List<String> getSource() {
        if (getConfiguration().getSource() == null) {
            return List.of();
        }
        return Collections.unmodifiableList(getConfiguration().getSource());
    }

    @Override
    public void shutdown() {
        log.info("Stopping docker compose {}...", getServerId());
        removeAllRoutes();
        setStatus(TigerServerStatus.STOPPED, "Docker compose " + getServerId() + " stopped");
    }
}
