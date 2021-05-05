package de.gematik.test.tiger.proxy.configuration;

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
    private boolean deactivateProxyLog = true;
    private String serverRootCaCertPem;
    private String serverRootCaKeyPem;
}
