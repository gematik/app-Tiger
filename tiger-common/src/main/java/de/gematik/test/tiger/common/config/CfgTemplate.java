/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CfgTemplate {

    private String templateName;
    private ServerType type;
    private List<String> source = new ArrayList<>();
    private String version;
    private Integer startupTimeoutSec;
    private boolean active = true;

    private CfgExternalJarOptions externalJarOptions;
    private CfgDockerOptions dockerOptions = new CfgDockerOptions();
    private CfgTigerProxyOptions tigerProxyCfg;

    private final List<CfgKey> pkiKeys = new ArrayList<>();
    /** list of env vars to be set for docker DONE, external Jar/TigerProxy TODO TGR-249 related to TGR-113 */
    private List<String> environment = new ArrayList<>();
    /** mappings for local tiger proxy to be set when this server is active */
    private final List<String> urlMappings = new ArrayList<>();
    /** properties to be exported to subsequent nodes as env vars and set as system properties to current jvm */
    private final List<String> exports = new ArrayList<>();
}
