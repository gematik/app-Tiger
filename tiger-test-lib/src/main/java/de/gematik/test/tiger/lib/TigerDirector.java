package de.gematik.test.tiger.lib;

import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TigerDirector {

    private static final Map<Long, RbelMessageProvider> rbelMsgProviderMap = new HashMap<>();
    private static TigerTestEnvMgr tigerTestEnvMgr;

    public static void beforeTestRun() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            return;
        }
        String cfgFile = OSEnvironment.getAsString("TIGER_CONFIG");
        // TODO read configuration including testenv var settings

        // TODO start single Tiger Proxy for local docker containers

        // TODO start TestEnvMgr
        tigerTestEnvMgr = new TigerTestEnvMgr();
        tigerTestEnvMgr.setUpEnvironment();
        // TODO store routes from server instances in static field for reuse by beforeTestThreadStart

    }

    public static void beforeTestThreadStart() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            return;
        }
        // check if testdirector was initialized

        // get route infos

        RbelMessageProvider rbelMessageProvider = new RbelMessageProvider();

        // instanatiate proxy and supply routes and register messageprovider as listener to proxy

        rbelMsgProviderMap.computeIfAbsent(tid(), key -> rbelMessageProvider);

    }

    public static String getProxySettings() {
        return tigerTestEnvMgr.getLocalDockerProxy().getBaseUrl();
    }
    public static RbelMessageProvider getRbelMessageProvider() {
        // get instance from map with thread id as key
        return Optional.ofNullable(rbelMsgProviderMap.get(tid()))
            .orElseThrow(() -> new TigerLibraryException("Tiger has not been initialized for Thread '%s'. "
                + "Did you call beforeTestThreadStart?", tid()));
    }

    public static long tid() {
        return Thread.currentThread().getId();
    }

}
