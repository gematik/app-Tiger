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

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@JsonInclude(Include.NON_NULL)
public class TigerProxyConfiguration {

    private List<TigerRoute> proxyRoutes;
    private ForwardProxyInfo forwardToProxy;
    @Builder.Default
    private String proxyLogLevel = "WARN";
    @Builder.Default
    private TigerTlsConfiguration tls = TigerTlsConfiguration.builder().build();
    private List<String> keyFolders;
    @Builder.Default
    private boolean activateRbelEndpoint = false;
    @Builder.Default
    private boolean activateAsn1Parsing = false;
    @Builder.Default
    private boolean activateForwardAllLogging = true;
    private TigerFileSaveInfo fileSaveInfo;
    private Integer proxyPort;
    @Builder.Default
    private boolean skipTrafficEndpointsSubscription = false;
    private List<String> trafficEndpoints;
    @Builder.Default
    private boolean downloadInitialTrafficFromEndpoints = false;
    @Builder.Default
    private String trafficEndpointFilterString = "";
    @Builder.Default
    private int maximumPartialMessageAgeInSeconds = 300;
    @Builder.Default
    private int connectionTimeoutInSeconds = 10;
    @Builder.Default
    private int stompClientBufferSizeInMb = 1;
    @Builder.Default
    private int perMessageBufferSizeInMb = 100;
    @Builder.Default
    private int rbelBufferSizeInMb = 1024;
    @Builder.Default
    private boolean activateRbelParsing = true;
    @Builder.Default
    private boolean activateVauAnalysis = false;
    @Builder.Default
    private TrafficEndpointConfiguration trafficEndpointConfiguration = new TrafficEndpointConfiguration();
    @Builder.Default
    private List<RbelModificationDescription> modifications = new ArrayList<>();
    @Builder.Default
    private boolean localResources = false;
    @Builder.Default
    private int maximumTrafficDownloadPageSize = 100_000;
    @Builder.Default
    private int trafficDownloadPageSize = 50;
    private String name;
    /**
     * Management-port of the Tiger Proxy.
     */
    private int adminPort;
    @Builder.Default
    private String filenamePattern = "tiger-report-${GEMATIKACCOUNT}-${DATE}-${TIME}.zip";
    @Builder.Default
    private String uploadUrl = "UNDEFINED";
    private String username;
    private String password;

    /**
     * used to overwrite proxyCfg with values that allow to reverse proxy the given server node.
     */
    private String proxiedServer;

    /**
     * Used when adding a route to the Tiger Proxy. By default, or when set to "inherit", the healthcheck-url-protocol is used here,
     * alternatively you may use http or https explicitly.
     */
    @Builder.Default
    private String proxiedServerProtocol = null;

    @JsonIgnore
    public Integer[] getPortAsArray() {
        if (proxyPort == null) {
            return null;
        } else {
            return new Integer[]{
                proxyPort
            };
        }
    }

    public Optional<ProxyConfiguration> convertForwardProxyConfigurationToMockServerConfiguration() {
        return Optional.ofNullable(getForwardToProxy())
            .flatMap(ForwardProxyInfo::createMockServerProxyConfiguration);
    }
}
