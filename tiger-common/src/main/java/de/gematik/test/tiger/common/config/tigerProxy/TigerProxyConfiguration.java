/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Builder.Default
    private TigerTlsConfiguration tls = TigerTlsConfiguration.builder().build();
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
}
