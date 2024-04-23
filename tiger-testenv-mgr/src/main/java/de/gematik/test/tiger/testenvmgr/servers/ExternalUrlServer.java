/*
 * Copyright (c) 2024 gematik GmbH
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

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

@TigerServerType("externalUrl")
public class ExternalUrlServer extends AbstractExternalTigerServer {

  @Builder
  public ExternalUrlServer(
      TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    if (StringUtils.isEmpty(getConfiguration().getHealthcheckUrl())) {
      getConfiguration().setHealthcheckUrl(getConfiguration().getSource().get(0));
    }
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    if (StringUtils.isEmpty(getConfiguration().getHealthcheckUrl())) {
      getConfiguration().setHealthcheckUrl(getConfiguration().getSource().get(0));
    }
    super.assertThatConfigurationIsCorrect();

    assertCfgPropertySet(getConfiguration(), "source");
  }

  @Override
  public void performStartup() {
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .type("externalUrl")
            .statusMessage("Starting external URL instance " + getServerId() + "...")
            .build());

    final var url = buildUrl();
    addServerToLocalProxyRouteMap(url);
    publishNewStatusUpdate(TigerServerStatusUpdate.builder().baseUrl(extractBaseUrl(url)).build());
    waitForServerUp();
  }

  @Override
  public void shutdown() {
    log.info("Stopping external url {}...", getServerId());
    setStatus(TigerServerStatus.STOPPED, "Disconnected external url " + getServerId());
  }

  @Override
  public Optional<String> getHealthcheckUrl() {
    if (StringUtils.isEmpty(getConfiguration().getHealthcheckUrl())) {
      return Optional.of(getConfiguration().getSource().get(0));
    } else {
      return Optional.ofNullable(getConfiguration().getHealthcheckUrl());
    }
  }

  private URL buildUrl() {
    try {
      return new URL(
          getTigerTestEnvMgr().replaceSysPropsInString(getConfiguration().getSource().get(0)));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          "Could not parse source URL '" + getConfiguration().getSource().get(0) + "'!", e);
    }
  }
}
