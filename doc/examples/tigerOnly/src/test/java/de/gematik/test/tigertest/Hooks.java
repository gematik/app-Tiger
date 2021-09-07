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

            try {
                final Map<String, String> env = Map.of("IDP_SERVER", "http://idp/.well-known/openid-configuration");
                OSEnvironment.setEnv(env);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }
}
