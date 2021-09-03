/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.*;

import java.io.IOException;
import java.util.List;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
public class TigerProxyConfiguration {

    private List<TigerRoute> proxyRoutes;
    private ForwardProxyInfo forwardToProxy;
    @Builder.Default
    private String proxyLogLevel = "WARN";
    private TigerPkiIdentity serverRootCa;
    private TigerPkiIdentity forwardMutualTlsIdentity;
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
    private int bufferSizeInKb = 1024;

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
