/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor(onConstructor_=@JsonIgnore)
@NoArgsConstructor
@Builder
public class TigerProxyConfiguration {

    private List<TigerRoute> proxyRoutes;
    private ForwardProxyInfo forwardToProxy;
    @Builder.Default
    private String proxyLogLevel = "WARN";
    private String serverRootCaCertPem;
    private String serverRootCaKeyPem;
    private RbelPkiIdentity serverRootCa;
    private List<String> keyFolders;
    @Builder.Default
    private boolean activateRbelEndpoint = false;
    @Builder.Default
    private boolean activateAsn1Parsing = true;
    @Builder.Default
    private boolean activateForwardAllLogging = true;
    private RbelFileSaveInfo fileSaveInfo;
    private Integer port;
    private List<String> trafficEndpoints;
    @Builder.Default
    private int connectionTimeoutInSeconds = 10;
    @Builder.Default
    private int bufferSizeInKb = 1024;

    public Integer[] getPortAsArray() {
        if (port == null) {
            return null;
        } else {
            return new Integer[]{
                port, port + 1
            };
        }
    }
}
