/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config;

import java.util.List;
import lombok.Data;

@Data
public class CfgHelmChartOptions {
    private String context;
    private String podName;
    private String workingDir;
    private String nameSpace;
    private boolean debug = false;
    /** list of regex names for pods to be running to signal successful startup of helm chart **/
    private List<String> healthcheckPods;

    private List<String> values;
    /**
     * comma separated list of port forwardings
     * Entries can be either "podNameRegex:xxxx", which is shorthand for "podNameRegex:xxxx:xxxx or
     * "podNameRegex:xxxx:yyyy" where xxxx is the local port and yyyy is the port in the pod
     */
    private List<String> exposedPorts;
}
