/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class CfgStandaloneProxy {
    private TigerProxyConfiguration tigerProxy;
}

