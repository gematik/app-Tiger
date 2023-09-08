/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CfgExternalJarOptions {

    private String workingDir;
    private List<String> options = new ArrayList<>();
    private List<String> arguments = new ArrayList<>();
    private boolean activateLogs = true;
    private boolean activateWorkflowLogs = true;
}
