/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.common.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.util.List;
import lombok.*;

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
    private TigerPkiIdentity serverRootCa;
    private TigerPkiIdentity forwardMutualTlsIdentity;
    private TigerPkiIdentity serverIdentity;
    private List<String> keyFolders;
    @Builder.Default
    private boolean activateRbelEndpoint = false;
    @Builder.Default
    private boolean activateAsn1Parsing = true;
    @Builder.Default
    private boolean activateForwardAllLogging = true;
    private RbelFileSaveInfo fileSaveInfo;
    private Integer port;
    @Builder.Default
    private boolean skipTrafficEndpointsSubscription = false;
    private List<String> trafficEndpoints;
    @Builder.Default
    private int connectionTimeoutInSeconds = 10;
    @Builder.Default
    private int stompClientBufferSizeInMb = 1;
    @Builder.Default
    private int perMessageBufferSizeInMb = 100;
    @Builder.Default
    private int rbelBufferSizeInMb = 1024;
    @Builder.Default
    private boolean disableRbelParsing = false;
    @Builder.Default
    private TrafficEndpointConfiguration trafficEndpointConfiguration = new TrafficEndpointConfiguration();

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
}
