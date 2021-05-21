package de.gematik.test.tigertest;

import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.lib.TigerDirector;
import io.cucumber.java.Before;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Hooks {

    @SneakyThrows
    @Before(order = 1)
    public void initializeTigerOnce() {
        if (!TigerDirector.isInitialized()) {
            TigerDirector.beforeTestRun();

            System.setProperty("http.proxyHost", "localhost");
            System.setProperty("http.proxyPort",
                "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
            System.setProperty("https.proxyHost", "localhost");
            System.setProperty("https.proxyPort",
                "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());

            // set proxy to local tigerproxy for erezept idp client
            // Unirest.config().proxy("localhost", TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());
            // TODO fd client uses unirest and has cert issue
            // Unirest.config().verifySsl(false);
            // RestAssured.useRelaxedHTTPSValidation();

            try {
                final Map<String, String> env = Map.of("IDP_SERVER", "http://idp/.well-known/openid-configuration");
                OSEnvironment.setEnv(env);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }
}
