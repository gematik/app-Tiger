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
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.*;
import org.mockserver.proxyconfiguration.ProxyConfiguration;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
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
    private RbelFileSaveInfo fileSaveInfo;
    private Integer port;
    @Builder.Default
    private boolean skipTrafficEndpointsSubscription = false;
    private List<String> trafficEndpoints;
    @Builder.Default
    private String trafficEndpointFilterString = "";
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

    @JsonIgnore
    public Integer[] getPortAsArray() {
        if (port == null) {
            return null;
        } else {
            return new Integer[]{
                port
            };
        }
    }

    public Optional<ProxyConfiguration> convertForwardProxyConfigurationToMockServerConfiguration() {
        return Optional.ofNullable(getForwardToProxy())
            .flatMap(ForwardProxyInfo::createMockServerProxyConfiguration);
    }
}
