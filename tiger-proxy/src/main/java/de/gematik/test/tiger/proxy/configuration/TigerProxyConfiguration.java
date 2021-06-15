/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import de.gematik.rbellogger.util.RbelPkiIdentity;
import java.util.List;
import java.util.Map;

import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TigerProxyConfiguration {

    private List<TigerRoute> proxyRoutes;
    private ForwardProxyInfo forwardToProxy;
    private String proxyLogLevel = "WARN";
    private String serverRootCaCertPem;
    private String serverRootCaKeyPem;
    private RbelPkiIdentity serverRootCa;
    private List<String> keyFolders;
    private boolean activateRbelEndpoint = false;
    private Integer port;
    private List<String> trafficEndpoints;

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
