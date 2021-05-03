package de.gematik.test.tiger.lib;

import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerDirector {

    private static final Map<Long, RbelMessageProvider> rbelMsgProviderMap = new HashMap<>();

    private static TigerTestEnvMgr tigerTestEnvMgr;

    private static boolean initialized = false;

    @SneakyThrows
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

        // set proxy to local tiger proxy for test suites
        log.info("setting tiger proxy...");
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort",
            "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());
        System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
        System.setProperty("https.proxyHost", "localhost");
        System.setProperty("https.proxyPort",
            "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());


        initialized = true;
        log.info("director is initialized OK");
    }

    public static synchronized  boolean isInitialized() {
        return initialized;
    }

    public static TigerTestEnvMgr getTigerTestEnvMgr() {
        return tigerTestEnvMgr;
    }

    public static void synchronizeTestCasesWIthPolaarion() {
        if (!checkIsInitialized()) {
            return;
        }
        // TODO call Polarion Toolbox via Java lang reflect to allow for soft coupling
    }

    public static void beforeTestThreadStart() {
        if (!checkIsInitialized()) {
            return;
        }
        // get route infos

        RbelMessageProvider rbelMessageProvider = new RbelMessageProvider();

        // instanatiate proxy and supply routes and register messageprovider as listener to proxy

        rbelMsgProviderMap.computeIfAbsent(tid(), key -> rbelMessageProvider);

    }

    public static void createAfoRepoort() {
        if (!checkIsInitialized()) {
            return;
        }
        // TODO create Aforeport and embedd it into serenity report
    }

    public static String getProxySettings() {
        if (!checkIsInitialized()) {
            return null;
        }
        return tigerTestEnvMgr.getLocalDockerProxy().getBaseUrl();
    }
    public static RbelMessageProvider getRbelMessageProvider() {
        if (!checkIsInitialized()) {
            return null;
        }
        // get instance from map with thread id as key
        return Optional.ofNullable(rbelMsgProviderMap.get(tid()))
            .orElseThrow(() -> new TigerLibraryException("Tiger has not been initialized for Thread '%s'. "
                + "Did you call TigerDirector.beforeTestThreadStart for this thread?", tid()));
    }

    public static long tid() {
        return Thread.currentThread().getId();
    }

    private static boolean checkIsInitialized() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.warn("Tiger test environment has not been initialized,"
                + "as the TIGER_ACTIVE environment variable is nto set to '1'.");
            return false;
        }
        if (!initialized) {
            throw new AssertionError("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
        }
        return true;
    }
}
