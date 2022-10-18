/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DirectReverseProxyInfo {

    private String hostname;
    private Integer port;
}
