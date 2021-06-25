/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.List;
import lombok.Data;

@Data
public class CfgTemplate {
    String name;
    String product;
    String type;
    List<String> source;
    String version;
    Integer startupTimeoutSec;
    List<CfgPKIKey> pkiKeys;
    List<String> urlMappings;
    List<String> environment;
    List<String> exports;
    String healthcheck;
    String workingDir;
    List<String> options;
    List<String> arguments;
}
