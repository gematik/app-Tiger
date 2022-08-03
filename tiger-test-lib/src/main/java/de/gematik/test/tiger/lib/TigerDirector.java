/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.reports.TigerRestAssuredCurlLoggingFilter;
import de.gematik.test.tiger.lib.serenityRest.SerenityRestUtils;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgrApplication;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.rest.SerenityRest;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Fail;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The TigerDirector is the public interface of the high level features of the Tiger test framework.
 * <ul>
 *     <li>read and apply Tiger test framework configuration from tiger.yaml</li>
 *     <li>start workflow UI, Tiger test environment manager and local Tiger Proxy</li>
 * </ul>
 * It also provides access to the Tiger test environment manager, the local Tiger Proxy and the Workflow UI interface.
 */
@SuppressWarnings("unused")
@Slf4j
public class TigerDirector {

    public static TigerRestAssuredCurlLoggingFilter curlLoggingFilter;
    private static TigerTestEnvMgr tigerTestEnvMgr;
    private static boolean initialized = false;

    @Getter
    private static TigerLibConfig libConfig;
    public static ConfigurableApplicationContext envMgrApplicationContext;

    public static synchronized void start() {
        if (initialized) {
            log.info("Tiger Director already started, skipping");
            return;
        }
        showTigerBanner();
        readConfiguration();
        registerRestAssuredFilter();
        applyTestLibConfig();
        // get free port
        startTestEnvMgr();
        tigerTestEnvMgr.getLocalTigerProxy().addRbelMessageListener(LocalProxyRbelMessageListener.rbelMessageListener);
        startWorkflowUi();
        setupTestEnvironent();
        setDefaultProxyToLocalTigerProxy();

        initialized = true;
    }

    private static boolean shutdownHookRegistered = false;

    public static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;

        log.info("Registering shutdown hook...");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (getLibConfig().isActivateWorkflowUi() && !tigerTestEnvMgr.isUserAcknowledgedShutdown()) {
                    System.out.println(
                        Ansi.colorize("TGR Workflow UI is active, please press quit in browser window...",
                            RbelAnsiColors.GREEN_BOLD));
                    if (tigerTestEnvMgr != null) {
                        tigerTestEnvMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder()
                            .bannerMessage("Test run finished, press QUIT")
                            .bannerColor("green")
                            .bannerType(BannerType.TESTRUN_ENDED)
                            .build());
                        try {
                            await().pollInterval(1, TimeUnit.SECONDS)
                                .atMost(5, TimeUnit.HOURS)
                                .until(() -> tigerTestEnvMgr.isUserAcknowledgedShutdown());
                        } finally {
                            tigerTestEnvMgr.shutDown();
                        }
                    }
                } else if (tigerTestEnvMgr != null) {
                    System.out.println("TGR Shutting down test env...");
                    tigerTestEnvMgr.shutDown();
                }
                unregisterRestAssuredFilter();
            } finally {
                System.out.println("TGR Destroying spring boot context after testrun...");
                if (envMgrApplicationContext != null) {
                    envMgrApplicationContext.close();
                }
                System.out.println("TGR Tiger shut down orderly");
            }
        }));
    }

    private static void setupTestEnvironent() {
        if (!TigerGlobalConfiguration.readBoolean("tiger.skipEnvironmentSetup", false)) {
            log.info("\n" + Banner.toBannerStr("SETTING UP TESTENV...", RbelAnsiColors.BLUE_BOLD.toString()));
            tigerTestEnvMgr.setUpEnvironment();
            log.info("\n" + Banner.toBannerStr("TESTENV SET UP OK", RbelAnsiColors.BLUE_BOLD.toString()));
        }
    }

    private static synchronized void readConfiguration() {
        libConfig = TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB")
            .orElseGet(TigerLibConfig::new);
    }

    private static void showTigerBanner() {
        // created via https://kirilllive.github.io/ASCII_Art_Paint/ascii_paint.html
        if (TigerGlobalConfiguration.readBoolean("TIGER_LOGO", false)) {
            try {
                log.info("\n" + IOUtils.toString(
                    Objects.requireNonNull(TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi")),
                    StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new TigerStartupException("Unable to read tiger logo!");
            }
        }
    }

    private static void applyTestLibConfig() {
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

    private static synchronized void startTestEnvMgr() {
        log.info("\n" + Banner.toBannerStr("STARTING TESTENV MGR...", RbelAnsiColors.BLUE_BOLD.toString()));
        envMgrApplicationContext = new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(Map.of("server.port",
                TigerGlobalConfiguration.readIntegerOptional("tiger.internal.testenvmgr.port").orElse(0)))
            .sources(TigerTestEnvMgrApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

        tigerTestEnvMgr = envMgrApplicationContext.getBean(TigerTestEnvMgr.class);
    }

    private static synchronized void startWorkflowUi() {
        if (libConfig.activateWorkflowUi) {
            log.info("\n" + Banner.toBannerStr("STARTING WORKFLOW UI ...", RbelAnsiColors.BLUE_BOLD.toString()));
            TigerTestEnvMgr.openWorkflowUiInBrowser(
                TigerGlobalConfiguration.readIntegerOptional("tiger.internal.testenvmgr.port").orElseThrow(
                        () -> new TigerEnvironmentStartupException("No free port for test environment manager reserved!"))
                    .toString());
            log.info("Waiting for workflow Ui to fetch status...");
            try {
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofSeconds(1))
                    .until(() -> tigerTestEnvMgr.isWorkflowUiSentFetch());
            } catch (ConditionTimeoutException cte) {
                libConfig.activateWorkflowUi = false;
                throw new TigerTestEnvException("No feedback from workflow Ui, aborting!", cte);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TigerTestEnvException("Interrupt received while waiting for workflow Ui to become ready", e);
            }
        }
    }

    private static synchronized void setDefaultProxyToLocalTigerProxy() {
        TigerProxyConfiguration tpCfg = tigerTestEnvMgr.getConfiguration().getTigerProxy();
        // set proxy to local tiger proxy for test suites
        if (tigerTestEnvMgr.isLocalTigerProxyActive()) {
            if (System.getProperty("http.proxyHost") != null || System.getProperty("https.proxyHost") != null) {
                log.info(Ansi.colorize("SKIPPING TIGER PROXY settings as System Property is set already...",
                    RbelAnsiColors.RED_BOLD));
            } else {
                log.info(Ansi.colorize(
                    "SETTING TIGER PROXY http://localhost:" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort()
                        + "...", RbelAnsiColors.BLUE_BOLD));
                System.setProperty("http.proxyHost", "localhost");
                System.setProperty("http.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
                System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
                System.setProperty("https.proxyHost", "localhost");
                System.setProperty("https.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
                System.setProperty("java.net.useSystemProxies", "true");
                SerenityRestUtils.setupSerenityRest(tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
            }
        } else {
            log.info(
                Ansi.colorize("SKIPPING TIGER PROXY settings as localProxyActive==false...", RbelAnsiColors.RED_BOLD));
        }
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    public static TigerTestEnvMgr getTigerTestEnvMgr() {
        assertThatTigerIsInitialized();
        return tigerTestEnvMgr;
    }

    public static String getLocalTigerProxyUrl() {
        assertThatTigerIsInitialized();
        if (tigerTestEnvMgr.getLocalTigerProxy() == null || !tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
            return null;
        } else {
            return tigerTestEnvMgr.getLocalTigerProxy().getBaseUrl();
        }
    }

    public static void waitForQuit() {
        if (getLibConfig().isActivateWorkflowUi()) {
            tigerTestEnvMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder()
                .bannerMessage("Press QUIT to abort test run")
                .bannerColor("green")
                .bannerType(BannerType.TESTRUN_ENDED)
                .build());
            try {
                await().pollInterval(1, TimeUnit.SECONDS)
                    .atMost(5, TimeUnit.HOURS)
                    .until(() -> tigerTestEnvMgr.isUserAcknowledgedShutdown());
            } finally {
                System.exit(0);
            }
        } else {
            tigerTestEnvMgr.waitForConsoleInput("quit");
            System.exit(0);
        }
    }

    private final static Pattern showSteps = Pattern.compile(
        ".*TGR (zeige|show) ([\\w|ü|ß]*) (Banner|banner|text|Text) \"(.*)\"");//NOSONAR

    private static void assertThatTigerIsInitialized() {
        if (!initialized) {
            throw new TigerStartupException("Tiger test environment has not been initialized successfully!");
        }
    }

    public static boolean isSerenityAvailable() {
        return TigerDirector.isSerenityAvailable(false);
    }

    public static boolean isSerenityAvailable(boolean quiet) {
        try {
            Class.forName("net.serenitybdd.core.Serenity");
            return true;
        } catch (ClassNotFoundException e) {
            if (!quiet) {
                log.warn(
                    "Trying to use Serenity functionality, but Serenity BDD packages are not declared as runtime dependency.",
                    e);
            }
            return false;
        }
    }

    public static void testUninitialize() {
        initialized = false;
        tigerTestEnvMgr = null;
        curlLoggingFilter = null;

        System.clearProperty("TIGER_TESTENV_CFGFILE");
        System.clearProperty("http.proxyHost");
        System.clearProperty("https.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyPort");

        TigerGlobalConfiguration.reset();
    }

    public static synchronized void registerRestAssuredFilter() {
        if (getLibConfig().isAddCurlCommandsForRaCallsToReport() && curlLoggingFilter == null) {
            curlLoggingFilter = new TigerRestAssuredCurlLoggingFilter();
            SerenityRest.filters(curlLoggingFilter);
        }
    }

    public static synchronized void unregisterRestAssuredFilter() {
        if (curlLoggingFilter != null) {
            SerenityRest.replaceFiltersWith(new ArrayList<>());
        }
        curlLoggingFilter = null;
    }

    public static void pauseExecution() {
        pauseExecution("");
    }

    public static void pauseExecution(String message) {
        String defaultMessage = "Test execution paused, click to continue";
        if (StringUtils.isBlank(message)) {
            message = defaultMessage;
        }

        if (getLibConfig().isActivateWorkflowUi()) {
            tigerTestEnvMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder()
                .bannerMessage(message)
                .bannerColor("green")
                .bannerType(BannerType.STEP_WAIT)
                .build());
            await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.HOURS)
                .until(() -> tigerTestEnvMgr.isUserAcknowledgedContinueTestRun());
            tigerTestEnvMgr.resetUserInput();
        } else {
            // TGR-585
            log.warn(String.format("The step 'TGR pause test run execution with message \"%s\"' is not supported outside the Workflow UI. Please check the manual for more information.", message));

        }
    }

    public static void pauseExecutionAndFailIfDesired(String message, String errorMessage) {
        if (getLibConfig().isActivateWorkflowUi()) {
            tigerTestEnvMgr.receiveTestEnvUpdate(TigerStatusUpdate.builder().bannerMessage(
                    message)
                .bannerColor("black")
                .bannerType(BannerType.FAIL_PASS)
                .build());
            await().pollInterval(1, TimeUnit.SECONDS)
                .atMost(5, TimeUnit.HOURS)
                .until(() -> tigerTestEnvMgr.isUserAcknowledgedContinueTestRun()
                    || tigerTestEnvMgr.isUserAcknowledgedFailingTestRun());
            if (tigerTestEnvMgr.isUserAcknowledgedFailingTestRun()) {
                tigerTestEnvMgr.resetUserInput();
                Fail.fail(errorMessage);
            } else {
                tigerTestEnvMgr.resetUserInput();
            }
        } else {
            // TGR-585
            log.warn(String.format(
                "The step 'TGR pause test run execution with message \"%s\" and message in case of error \"%s\"' is not supported outside the Workflow UI. Please check the manual for more information.", message, errorMessage));
        }
    }
}
