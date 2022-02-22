/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.hooks.TigerTestHooks;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.monitor.MonitorUI;
import de.gematik.test.tiger.lib.parser.model.gherkin.Step;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import io.restassured.RestAssured;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * The TigerDirector is the public interface of the high level features of the Tiger test framework.
 * <ul>
 *     <li>read and apply Tiger test framework configuration from tiger.yaml</li>
 *     <li>start monitoring UI, Tiger test environment manager and local Tiger Proxy</li>
 *     <li>Sync test cases with Polarion</li>
 *     <li>Sync test reports with Aurora and Polarion</li>
 *     <li>Create requirement coverage report based on @Afo annotations and requirements downloaded from Polarion</li>
 * </ul>
 * It also provides access to the Tiger test environment manager, the local Tiger Proxy and the Monitor UI interface.
 */
@SuppressWarnings("unused")
@Slf4j
public class TigerDirector {

    private static TigerTestEnvMgr tigerTestEnvMgr;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Optional<MonitorUI> optionalMonitorUI = Optional.empty();
    private static boolean initialized = false;

    @Getter
    private static TigerLibConfig libConfig = new TigerLibConfig();

    public static synchronized void readConfiguration() {
        if (!TigerGlobalConfiguration.readBoolean("TIGER_NOLOGO", false)) {
            try {
                log.info("\n" + IOUtils.toString(
                    Objects.requireNonNull(TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi")),
                    StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new TigerStartupException("Unable to read tiger logo!");
            }
        }
        log.info("\n" + Banner.toBannerStr("READING TIGER LIB CONFIG...", RbelAnsiColors.BLUE_BOLD.toString()));
        File cfgFile = new File("tiger.yml");
        if (!cfgFile.exists()) {
            cfgFile = new File("tiger.yaml");
        }
        if (!cfgFile.exists()) {
            log.warn("No Tiger configuration file found (tiger.yaml, tiger.yml)! Continuing with default values");
            libConfig = new TigerLibConfig();
        } else {
            try {
                TigerGlobalConfiguration.readFromYaml(FileUtils.readFileToString(cfgFile, StandardCharsets.UTF_8), "TIGER_LIB");
                libConfig = TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB");
            } catch (IOException e) {
                throw new TigerStartupException("Error while reading configuration file '" + cfgFile.getAbsolutePath(), e);
            }
        }
    }

    public static void applyTestLibConfig() {
        if (libConfig.isRbelPathDebugging()) {
            RbelOptions.activateRbelPathDebugging();
        } else {
            RbelOptions.deactivateRbelPathDebugging();
        }
        if (libConfig.isRbelAnsiColors()) {
            RbelOptions.activateAnsiColors();
        } else {
            RbelOptions.deactivateAnsiColors();
        }
    }

    public static synchronized void startMonitorUITestEnvMgrAndTigerProxy() {
        TigerTestHooks.assertTigerActive();

        if (libConfig.activateMonitorUI) {
            try {
                optionalMonitorUI = MonitorUI.getMonitor();
            } catch (HeadlessException hex) {
                log.error("Unable to start Monitor UI on a headless server!", hex);
            }
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
        if (tigerTestEnvMgr.isLocalTigerProxyActive()) {
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
                setupSerenityRest();
            }
        } else {
            log.info(Ansi.colorize("SKIPPING TIGER PROXY settings...", RbelAnsiColors.RED_BOLD));
        }

        // TODO TGR-295 DO NOT DELETE!
        // set proxy to local tigerproxy for erezept idp client
        // Unirest.config().proxy("localhost", TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());

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

        if (TigerGlobalConfiguration.readBoolean("TIGER_SYNC_TESTCASES", false)) {
            try {
                Method polarionToolBoxMain = Class.forName("de.gematik.polarion.toolbox.ToolBox")
                    .getDeclaredMethod("main", String[].class);
                String[] args = new String[]{"-m", "tcimp", "-dryrun"};
                // TODO TGR-251 - read from tiger-testlib.yaml or env vars values for -h -u -p -prj -aq -fd -f -bdd

                log.info("Syncing test cases with Polarion...");
                polarionToolBoxMain.invoke(null, (Object[]) args);
                log.info("Test cases synched with Polarion...");
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                throw new TigerLibraryException("Unable to access Polarion Toolbox! "
                    + "Be sure to have it included in mvn dependencies.", e);
                // TODO TGR-258 add the mvn dependency lines to log output
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new TigerLibraryException("Unable to call Polarion Toolbox's main method!", e);
            }
        }
    }

    public static void createAfoRepoort() {
        assertThatTigerIsInitialized();
        // TODO TGR-259 (see architecture decision about pluggable (TGR-253)) create Aforeport and embedd it into serenity report
    }

    public static String getProxySettings() {
        assertThatTigerIsInitialized();
        if (tigerTestEnvMgr.getLocalTigerProxy() == null || !tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
            return null;
        } else {
            return tigerTestEnvMgr.getLocalTigerProxy().getBaseUrl();
        }
    }

    public static void waitForQuit() {
        optionalMonitorUI.ifPresentOrElse(
            (monitor) -> monitor.waitForQuit("Tiger Testsuite"),
            () -> TigerTestEnvMgr.waitForQuit("Tiger Testsuite"));
    }

    public static void updateStepInMonitor(Step step) {
        optionalMonitorUI.ifPresent((monitor) -> monitor.updateStep(step));
    }

    private static void assertThatTigerIsInitialized() {
        if (!TigerGlobalConfiguration.readBoolean("TIGER_ACTIVE")) {
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

        TigerGlobalConfiguration.reset();
    }

    private static class TigerSerenityRestException extends RuntimeException {

        public TigerSerenityRestException(String s, Exception e) {
            super(s, e);
        }
    }
}
