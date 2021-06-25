/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.exceptions;

import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.Getter;

@Getter
public class TigerProxyRouteConflictException extends TigerProxyConfigurationException {

    private final TigerRoute existingRoute;

    public TigerProxyRouteConflictException(TigerRoute existingRoute) {
        super("Could not add route. Competing route found: " + existingRoute);

        this.existingRoute = existingRoute;
    }
}
