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

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.data.config.CfgDockerOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;

@Slf4j
public class DockerComposeServer extends TigerServer {

    @Builder
    DockerComposeServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, tigerTestEnvMgr, configuration);
    }

    @Override
    public void performStartup() {
        log.info(Ansi.colorize("Starting docker compose for {} :{}", RbelAnsiColors.GREEN_BOLD),
            getHostname(), getDockerSource());
        statusMessage("Starting docker compose");
        getTigerTestEnvMgr().getDockerManager().startComposition(this);

        // add routes needed for each server to local docker proxy
        // ATTENTION only one route per server!
        if (getConfiguration().getDockerOptions().getPorts() != null
            && !getConfiguration().getDockerOptions().getPorts().isEmpty()) {
            addRoute(TigerRoute.builder()
                .from(HTTP + getHostname())
                .to(HTTP + "localhost:" + getConfiguration().getDockerOptions().getPorts().values().iterator().next())
                .build());
        }
        log.info(Ansi.colorize("Docker compose Startup for {} : {} OK", RbelAnsiColors.GREEN_BOLD),
            getHostname(), getDockerSource());
        statusMessage("Docker compose started");
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
        log.info("Stopping docker compose {}...", getHostname());
        removeAllRoutes();
    }
}
