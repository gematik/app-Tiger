/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class TigerProxyReference {
    TigerProxy proxy;
}
