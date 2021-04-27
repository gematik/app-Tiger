package de.gematik.test.tiger.lib;

import java.util.Optional;

public class TigerDirector {

    public static void init() {
        String active = getEnvOrSystemProperty("TIGER_ACTIVE");
        if (active == null || !active.equals("1")) {
            return;
        }
        String cfgFile = getEnvOrSystemProperty("TIGER_CONFIG");
        // TODO read configuration including testenv var settings

        // TODO start TestEnvMgr
        //TigerTestEnvMgr envmgr
    }

    public static String getEnvOrSystemProperty(String name) {
        return Optional.ofNullable(System.getenv(name)).orElse(System.getProperty(name));
    }
}
