/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.data.config.CfgTemplate;
import lombok.Data;

@Data
public class CfgServer extends CfgTemplate {

    private String hostname;
    /**
     * References a template to set up the server. You can override properties.
     */
    private String template;
    /**
     * References another server which has to be booted prior to this. Multiple servers can be referenced, divided by
     * comma.
     */
    private String dependsUpon;
}
