/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
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
    @Builder.Default
    private boolean localResources = false;
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
     * Used to add a route to the Tiger Proxy. By default, the healthcheck-url-protocol is used here, or http if none is
     * present. If you want to override this you can do it using this field.
     */
    private String proxiedServerProtocol;

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
