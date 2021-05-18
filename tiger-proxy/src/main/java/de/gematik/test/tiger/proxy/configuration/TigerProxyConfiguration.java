/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TigerProxyConfiguration {

    private Map<String, String> proxyRoutes;
    private ForwardProxyInfo forwardToProxy;
    private String proxyLogLevel = "WARN";
    private String serverRootCaCertPem;
    private String serverRootCaKeyPem;
    private List<String> keyFolders;
}
