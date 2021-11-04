/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.proxy.RbelMessageProvider;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

/**
 * The TigerDirector is the public interface to the tiger test suite, the tige rtestenv manager and the tiger proxies.
 */
@SuppressWarnings("unused")
@Slf4j
public class TigerDirector {

    private TigerDirector() {
    }

    /**
     * Thread id based map of rbel message providers. For each thread this provider receives and collects all rbel
     * messages from the proxy assigned to the current thread.
     */
    private static final Map<Long, RbelMessageProvider> rbelMsgProviderMap = new HashMap<>();

    /**
     * Thread id based map of tiger proxies. For each thread a separate proxy is instantiated to ensure the traffic is
     * assigned to the correct test runner thread / test step.
     * TODO CRITICAL make sure we can do proxiing thread based!
     * https://stackoverflow.com/questions/16388112/each-thread-using-its-own-proxy
     */
    private static final Map<Long, TigerProxy> proxiesMap = new HashMap<>();

    private static TigerTestEnvMgr tigerTestEnvMgr;

    private static boolean initialized = false;

    public static synchronized void beforeTestRun() {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            log.warn(Ansi.colorize("ABORTING initialisation as environment variable TIGER_ACTIVE is not set to '1'",
                RbelAnsiColors.RED_BOLD));
            throw new AssertionError("ABORTING initialisation as environment variable TIGER_ACTIVE is not set to '1'");
        }

        if (OsEnvironment.getAsString("TIGER_NOLOGO") == null) {
            try {
                log.info("\n" + IOUtils.toString(
                    Objects.requireNonNull(TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi")),
                    StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new TigerStartupException("Unable to read tiger logo!");
            }
        }

        log.info("\n" + Banner.toBannerStr("READING TEST CONFIG...", RbelAnsiColors.BLUE_BOLD.toString()));
        File cfgFile = new File("tiger.yml");
        if (!cfgFile.exists()) {
            cfgFile = new File("tiger.yaml");
        }
        TigerLibConfig config;
        if (cfgFile.exists()) {
            config = new TigerConfigurationHelper<TigerLibConfig>().yamlReadOverwriteToConfig(cfgFile.getAbsolutePath(), "TIGER_LIB", TigerLibConfig.class);
        } else {
            log.warn("No Tiger configuration file found (tiger.yaml, tiger.yml)! Continuing with default values");
            config = new TigerLibConfig();
        }

        if (config.isRbelPathDebugging()) {
            RbelOptions.activateRbelPathDebugging();
        } else {
            RbelOptions.deactivateRbelPathDebugging();
        }
        if (config.isRbelAnsiColors()) {
            RbelOptions.activateAnsiColors();
        } else {
            RbelOptions.deactivateAnsiColors();
        }
        log.info("\n" + Banner.toBannerStr("STARTING TESTENV MGR...", RbelAnsiColors.BLUE_BOLD.toString()));
        tigerTestEnvMgr = new TigerTestEnvMgr();
        tigerTestEnvMgr.setUpEnvironment();

        TigerProxyConfiguration tpCfg = tigerTestEnvMgr.getConfiguration().getTigerProxy();
        if (tpCfg.isSkipTrafficEndpointsSubscription()) {
            log.info("Trying to late connect to traffic endpoints...");
            tigerTestEnvMgr.getLocalTigerProxy().subscribeToTrafficEndpoints(tpCfg);
        }

        // set proxy to local tiger proxy for test suites
        if (tigerTestEnvMgr.getLocalTigerProxy() != null && tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
            if (System.getProperty("http.proxyHost") != null || System.getProperty("https.proxyHost") != null) {
                log.info(Ansi.colorize("SKIPPING TIGER PROXY settings as System Property is set already...",
                    RbelAnsiColors.RED_BOLD));
            } else {
                log.info(Ansi.colorize("SETTING TIGER PROXY...", RbelAnsiColors.BLUE_BOLD));
                System.setProperty("http.proxyHost", "localhost");
                System.setProperty("http.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getPort());
                System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
                System.setProperty("https.proxyHost", "localhost");
                System.setProperty("https.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getPort());
            }
        } else {
            log.info(Ansi.colorize("SKIPPING TIGER PROXY settings...", RbelAnsiColors.RED_BOLD));
        }
        setupSerenityRest();

        initialized = true;
        log.info("\n" + Banner.toBannerStr("DIRECTOR STARTUP OK", RbelAnsiColors.GREEN_BOLD.toString()));
    }

    private static void setupSerenityRest() {
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

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    public static TigerTestEnvMgr getTigerTestEnvMgr() {
        assertThatTigerIsInitialized();
        return tigerTestEnvMgr;
    }

    public static void synchronizeTestCasesWithPolarion() {
        assertThatTigerIsInitialized();

        if (OsEnvironment.getAsBoolean("TIGER_SYNC_TESTCASES")) {
            try {
                Method polarionToolBoxMain = Class.forName("de.gematik.polarion.toolbox.ToolBox")
                    .getDeclaredMethod("main", String[].class);
                String[] args = new String[]{"-m", "tcimp", "-dryrun"};
                // TODO read from tiger-testlib.yaml or env vars values for -h -u -p -prj -aq -fd -f -bdd

                log.info("Syncing test cases with Polarion...");
                polarionToolBoxMain.invoke(null, (Object[]) args);
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
        assertThatTigerIsInitialized();
        if (proxiesMap.containsKey(tid())) {
            log.warn("Proxy for given thread '" + tid() + "' already initialized!");
            return;
        }
        // instantiate proxy and supply routes and register message provider as listener to proxy
        final var threadProxy = new TigerProxy(tigerTestEnvMgr.getConfiguration().getTigerProxy());
        getTigerTestEnvMgr().getRoutes().forEach(threadProxy::addRoute);
        threadProxy.addRbelMessageListener(rbelMsgProviderMap.computeIfAbsent(tid(), key -> new RbelMessageProvider()));
        proxiesMap.putIfAbsent(tid(), threadProxy);
    }

    public static void createAfoRepoort() {
        assertThatTigerIsInitialized();
        // TODO create Aforeport and embedd it into serenity report
    }

    public static String getProxySettings() {
        assertThatTigerIsInitialized();
        if (tigerTestEnvMgr.getLocalTigerProxy() == null) {
            return null;
        } else {
            return tigerTestEnvMgr.getLocalTigerProxy().getBaseUrl();
        }
    }

    public static RbelMessageProvider getRbelMessageProvider() {
        assertThatTigerIsInitialized();
        // get instance from map with thread id as key
        return Optional.ofNullable(rbelMsgProviderMap.get(tid()))
            .orElseThrow(() -> new TigerLibraryException("Tiger has not been initialized for Thread '%s'. "
                + "Did you call TigerDirector.beforeTestThreadStart for this thread?", tid()));
    }

    public static long tid() {
        return Thread.currentThread().getId();
    }

    private static void assertThatTigerIsInitialized() {
        if (!OsEnvironment.getAsBoolean("TIGER_ACTIVE")) {
            throw new TigerStartupException("Tiger test environment has not been initialized,"
                + "as the TIGER_ACTIVE environment variable is not set to '1'.");
        }
        if (!initialized) {
            throw new TigerStartupException("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
        }
    }

    static void testUninitialize() {
        initialized = false;
        tigerTestEnvMgr = null;

        System.clearProperty("TIGER_ACTIVE");
        System.clearProperty("TIGER_TESTENV_CFGFILE");
        System.clearProperty("http.proxyHost");
        System.clearProperty("https.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyPort");

    }

    private static class TigerSerenityRestException extends RuntimeException {
        public TigerSerenityRestException(String s, Exception e) {
            super(s, e);
        }
    }
}
