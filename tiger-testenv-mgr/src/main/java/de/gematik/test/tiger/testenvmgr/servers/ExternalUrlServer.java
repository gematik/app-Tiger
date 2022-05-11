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
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ExternalUrlServer extends AbstractExternalTigerServer {

    @Builder
    ExternalUrlServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        log.info(Ansi.colorize("starting external URL instance {}...", RbelAnsiColors.GREEN_BOLD), getHostname());

        final var url = buildUrl();
        addServerToLocalProxyRouteMap(url);
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .baseUrl(extractBaseUrl(url))
            .build());

        log.info("  Waiting 50% of start up time for external URL instance  {} to come up ...", getHostname());

        waitForService(true);
        if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
        }
    }

    @Override
    public void shutdown() {
        removeAllRoutes();
    }

    @Override
    String getHealthcheckUrl() {
        if (getConfiguration().getExternalJarOptions() == null
        || StringUtils.isEmpty(getConfiguration().getHealthcheckUrl())){
            return getConfiguration().getSource().get(0);
        } else {
            return getConfiguration().getHealthcheckUrl();
        }
    }

    private URL buildUrl() {
        try {
            return new URL(getTigerTestEnvMgr().replaceSysPropsInString(getConfiguration().getSource().get(0)));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not parse source URL '" + getConfiguration().getSource().get(0) + "'!", e);
        }
    }
}
