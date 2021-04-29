package de.gematik.test.tiger.lib;

import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerDirector {

    private static final Map<Long, RbelMessageProvider> rbelMsgProviderMap = new HashMap<>();
    private static TigerTestEnvMgr tigerTestEnvMgr;

    private static boolean initialized = false;

    public static synchronized void beforeTestRun() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.warn("ABORTING initialisation as environment variable TIGER_ACTIVE is not set to '1'");
            return;
        }
        log.info("reading test configuration...");
        String cfgFile = OSEnvironment.getAsString("TIGER_CONFIG");
        // TODO read configuration including testenv var settings

        log.info("starting test environment manager...");
        tigerTestEnvMgr = new TigerTestEnvMgr();
        tigerTestEnvMgr.setUpEnvironment();
        // TODO store routes from server instances in static field for reuse by beforeTestThreadStart

        initialized = true;
        log.info("director is initialized OK");
    }

    public static void beforeTestThreadStart() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            return;
        }
        checkIsInitialized();
        checkIsInitialized();
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
                + "Did you call TigerDirector.beforeTestThreadStart for this thread?", tid()));
    }

    public static long tid() {
        return Thread.currentThread().getId();
    }

    private static void checkIsInitialized() {
        if (!initialized) {
            throw new AssertionError("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
        }
    }
}
