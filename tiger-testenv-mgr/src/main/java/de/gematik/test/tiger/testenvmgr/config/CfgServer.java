/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CfgServer {
    private String name;
    private String template;
    /** TODO brauchen wir das noch? */
    private CfgProductType product;
    private ServerType type;
    private List<String>  source= new ArrayList<>();
    private String version;
    private String workingDir;
    private List<String> options = new ArrayList<>();
    private List<String> arguments = new ArrayList<>();
    private Integer startupTimeoutSec;
    private String healthcheck;
    private List<String> serviceHealthchecks;
    private boolean active = true;
    private final List<CfgKey> pkiKeys = new ArrayList<>();
    private boolean proxied = true;
    /** For docker type to trigger OneShotSTartupStrategy
     * @see org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
     */
    private boolean oneShot = false;
    private String entryPoint;
    private final List<String> environment = new ArrayList<>();
    private final List<String> urlMappings = new ArrayList<>();
    private final List<String> exports = new ArrayList<>();
    private Map<Integer, Integer> ports;

    private CfgReverseProxy reverseProxyCfg;

    @JsonIgnore
    private boolean started = false;
}

/**
 * reverse proxy
 *
 * type
 * (active) true
 * name
 * version
 * (template) none
 * (workingDir) TEMPFOLDER
 * (options) none
 * (arguments) none
 * reverseProxy:
 *   (repo) nexus
 *   serverPort
 *   proxiedServer
 *   proxyPort
 *   (proxyProtocal) http
 *   proxyCfg
 */
