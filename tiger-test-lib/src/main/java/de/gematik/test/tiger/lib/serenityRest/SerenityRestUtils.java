/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.serenityRest;

import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SerenityRestUtils {

    public static void setupSerenityRest(int localTigerProxyProxyPort) {
        RestAssured.proxy("localhost", localTigerProxyProxyPort);

        RestAssured.filters((requestSpec, responseSpec, ctx) -> {
            try {
                log.trace("Sending Request "
                    + requestSpec.getMethod() + " " + requestSpec.getURI()
                    + " via proxy " + requestSpec.getProxySpecification());
                return ctx.next(requestSpec, responseSpec);
            } catch (Exception e) {
                throw new TigerSerenityRestException("Error while retrieving "
                    + requestSpec.getMethod() + " " + requestSpec.getURI()
                    + " via proxy " + requestSpec.getProxySpecification(), e);
            }
        });
    }

    private static class TigerSerenityRestException extends RuntimeException {

        public TigerSerenityRestException(String s, Exception e) {
            super(s, e);
        }
    }
}
