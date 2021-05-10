package de.gematik.test.tiger.lib;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

/**
 * The TigerDirector is the public interface to the tiger test suite, the tige rtestenv manager and the tiger proxies.
 */
@Slf4j
public class TigerDirector {

    /**
     * Thread id based map of rbel message providers. For each thread this provider receives and collects
     * all rbel messages from the proxy assigned to the current thread.
     */
    private static final Map<Long, RbelMessageProvider> rbelMsgProviderMap = new HashMap<>();

    /**
     * Thread id based map of tiger proxies. For each thread a separate proxy is instantiated to ensure
     * the traffic is assigned to the correct test runner thread / test step.
     * TODO CRITICAL make sure we can do proxiing thread based!
     * https://stackoverflow.com/questions/16388112/each-thread-using-its-own-proxy
     */
    private static final Map<Long, TigerProxy> proxiesMap = new HashMap<>();

    private static TigerTestEnvMgr tigerTestEnvMgr;

    private static boolean initialized = false;

    @SneakyThrows
    public static synchronized void beforeTestRun() {
        if (!OSEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.warn(Ansi.BOLD + Ansi.RED + "ABORTING initialisation as environment variable TIGER_ACTIVE is not set to '1'" + Ansi.RESET);
            throw new AssertionError("ABORTING initialisation as environment variable TIGER_ACTIVE is not set to '1'");
        }

        log.info("\n" + IOUtils.toString(TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi"), StandardCharsets.UTF_8));
        log.info("\n" + Banner.toBannerStr("READING TEST CONFIG...", Ansi.BOLD + Ansi.BLUE));
        String cfgFile = OSEnvironment.getAsString("TIGER_CONFIG");
        // TODO read configuration including testenv var settings

        log.info("\n" + Banner.toBannerStr("STARTING TESTENV MGR...", Ansi.BOLD + Ansi.BLUE));
        tigerTestEnvMgr = new TigerTestEnvMgr();
        tigerTestEnvMgr.setUpEnvironment();

        // set proxy to local tiger proxy for test suites
        log.info("\n" + Banner.toBannerStr("SETTING TIGER PROXY...", Ansi.BOLD + Ansi.BLUE));
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort",
            "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());
        System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
        System.setProperty("https.proxyHost", "localhost");
        System.setProperty("https.proxyPort",
            "" + TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());


        initialized = true;
        log.info("\n" + Banner.toBannerStr("DIRECTOR STARTUP OK", Ansi.BOLD + Ansi.BLUE));
    }

    public static synchronized  boolean isInitialized() {
        return initialized;
    }

    public static TigerTestEnvMgr getTigerTestEnvMgr() {
        return tigerTestEnvMgr;
    }

    public static void synchronizeTestCasesWithPolarion() {
        if (!checkIsInitialized()) {
            return;
        }

        if (OSEnvironment.getAsBoolean("TIGER_SYNC_TESTCASES")) {
            try {
                Method polarionToolBoxMain = Class.forName("de.gematik.polarion.toolbox.ToolBox")
                    .getDeclaredMethod("main", String[].class);
                String[] args = new String[]{"-m", "tcimp", "-dryrun"};
                // TODO read from tiger-testlib.yaml or env vars values for -h -u -p -prj -aq -fd -f -bdd

                log.info("Syncing test cases with Polarion...");
                polarionToolBoxMain.invoke(null, args);
                log.info("Test cases synched with Polarion...");
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new TigerLibraryException("Unable to access Polarion Toolbox! "
                    + "Be sure to have it included in mvn dependencies.", e);
                // TODO add the mvn dependency lines to log output
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new TigerLibraryException("Unable to call Polarion Toolbox's main method!", e);
            }
        }
    }

    public static void beforeTestThreadStart() {
        if (!checkIsInitialized()) {
            return;
        }
        if (proxiesMap.containsKey(tid())) {
            log.warn("Proxy for given thread '" + tid() + "' already initialized!");
            return;
        }
        // instanatiate proxy and supply routes and register messageprovider as listener to proxy
        TigerProxy threadProxy = new TigerProxy(tigerTestEnvMgr.getConfiguration().getTigerProxy());
        getTigerTestEnvMgr().getRoutes().forEach(route -> threadProxy.addRoute(route[0], route[1]));

        RbelMessageProvider rbelMessageProvider = new RbelMessageProvider();
        rbelMsgProviderMap.computeIfAbsent(tid(), key -> rbelMessageProvider);
        threadProxy.addRbelMessageListener(rbelMessageProvider);
        proxiesMap.computeIfAbsent(tid(), key -> threadProxy);
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
