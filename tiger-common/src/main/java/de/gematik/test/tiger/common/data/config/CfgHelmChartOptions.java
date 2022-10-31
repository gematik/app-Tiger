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

    private List<String> values;
    /** either "xxxx", which is shorthand for "xxxx:xxxx or "xxxx:yyyy" where xxxx is the local port and yyyy is the port in the pod */
    private List<String> exposedPorts;
}
