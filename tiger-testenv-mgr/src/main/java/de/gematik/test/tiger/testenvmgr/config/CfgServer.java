/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.common.config.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CfgServer extends CfgTemplate {

    private String template;
    @JsonIgnore
    private boolean started = false;
}

/* Docker
 ** name
 ** type
 ** source
 * version
 ** (startupTimeoutSec) 20
 ** (pkiKeys) []
 * +(proxied) true
 * +(oneShot) false
 * +(entryPoint) as configured in container
 * +(ports) used in memory only
 ** (environment) []
 ** (urlMappings) []
 ** (exports) []
 */


/* Tiger proxy
 ** name
 ** type
 * (source) nexus [ nexus, maven, specific URL ], nexus and maven will be replaced by exact download URL
 * version
 ** (startupTimeoutSec) 20
 ** (pkiKeys) []
 * (workingDir) TEMP folder
 * (healthcheck) will be set to localhost and tiger proxies server port
 * (options) none
 * (arguments) none, will be expanded with spring profile param
 * tigerProxyCfg:
 *   serverPort
 *   (proxiedServer) none
 *   proxyPort
 *   (proxyProtocal) http
 *   proxyCfg:
 *     ff....
 ** (environment) []
 ** (urlMappings) []
 ** (exports) []
 */

/**
 * name template product type source version workingDir options arguments startupTimeoutSec healthcheck
 * serviceHealthchecks (active) true pkiKeys proxied oneShot entryPoint environment urlMappings exports ports
 * tigerProxyCfg
 */

