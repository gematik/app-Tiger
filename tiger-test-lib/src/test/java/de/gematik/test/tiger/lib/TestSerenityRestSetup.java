/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;


import static org.assertj.core.api.Assertions.assertThat;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.Test;

@Slf4j
class TestSerenityRestSetup {

    @Test
    void trustStoreIsSet_ShouldBeValidRequestToHTTPS() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/trustStoreTest.yaml");

        try {
            Serenity.throwExceptionsImmediately();
            TigerDirector.start();
            assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().isLocalProxyActive()).isTrue();
            UnirestInstance unirestInstance = Unirest.spawnInstance();
            unirestInstance.config()
                .proxy("localhost", TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getProxyPort());
            try {
                unirestInstance.get("https://blub").asString();
            } catch (UnirestException ex) {
                ex.printStackTrace();
            }
            assertThat(SerenityRest
                .with().get("https://blub").getStatusCode()).isEqualTo(200);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
        } finally {
            TigerDirector.getTigerTestEnvMgr().shutDown();
        }
    }
}
