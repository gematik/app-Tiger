/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.common.config.CfgTemplate;
import lombok.Data;

@Data
public class CfgServer extends CfgTemplate {

    private String hostname;
    private String template;
    private String dependsUpon;
    @JsonIgnore
    private boolean started = false;
}
