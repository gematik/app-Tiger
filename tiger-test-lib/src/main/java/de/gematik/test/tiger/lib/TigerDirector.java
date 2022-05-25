/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib;

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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The TigerDirector is the public interface of the high level features of the Tiger test framework.
 * <ul>
 *     <li>read and apply Tiger test framework configuration from tiger.yaml</li>
 *     <li>start workflow UI, Tiger test environment manager and local Tiger Proxy</li>
 *     <li>Sync test cases with Polarion</li>
 *     <li>Sync test reports with Aurora and Polarion</li>
 *     <li>Create requirement coverage report based on @Afo annotations and requirements downloaded from Polarion</li>
 * </ul>
 * It also provides access to the Tiger test environment manager, the local Tiger Proxy and the Monitor UI interface.
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
        startTestEnvMgr(); // pass in
        tigerTestEnvMgr.getLocalTigerProxy().addRbelMessageListener(LocalProxyRbelMessageListener.rbelMessageListener);
        startWorkflowUi(); // pass in
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
                if (getLibConfig().isActivateWorkflowUi()) {
                    System.out.println(Ansi.colorize("TGR Workflow UI is active, please press quit in browser window...", RbelAnsiColors.GREEN_BOLD));
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
            log.info("\n" + Banner.toBannerStr("SETTING UP TESTENV ...", RbelAnsiColors.BLUE_BOLD.toString()));
            tigerTestEnvMgr.setUpEnvironment();
            log.info("\n" + Banner.toBannerStr("TESTENV SET UP OK", RbelAnsiColors.BLUE_BOLD.toString()));
        }
    }

    private static synchronized void readConfiguration() {
        libConfig = TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB")
            .orElseGet(TigerLibConfig::new);
    }

    private static void showTigerBanner() {
        if (!TigerGlobalConfiguration.readBoolean("TIGER_NOLOGO", false)) {
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
                TigerGlobalConfiguration.readIntegerOptional("free.port.255").orElse(0)))
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
            TigerTestEnvMgr.openWorkflowUiInBrowser(TigerGlobalConfiguration.readIntegerOptional("free.port.255").get().toString());
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
                log.info(Ansi.colorize("SETTING TIGER PROXY http://localhost:" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort() + "...", RbelAnsiColors.BLUE_BOLD));
                System.setProperty("http.proxyHost", "localhost");
                System.setProperty("http.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
                System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
                System.setProperty("https.proxyHost", "localhost");
                System.setProperty("https.proxyPort", "" + tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
                System.setProperty("java.net.useSystemProxies", "true");
                SerenityRestUtils.setupSerenityRest(tigerTestEnvMgr.getLocalTigerProxy().getProxyPort());
            }
        } else {
            log.info(Ansi.colorize("SKIPPING TIGER PROXY settings as localProxyActive==false...", RbelAnsiColors.RED_BOLD));
        }

        // TODO TGR-295 DO NOT DELETE!
        // set proxy to local tigerproxy for erezept idp client
        // Unirest.config().proxy("localhost", TigerDirector.getTigerTestEnvMgr().getLocalDockerProxy().getPort());
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

    public static String getLocalTigerProxyUrl() {
        assertThatTigerIsInitialized();
        if (tigerTestEnvMgr.getLocalTigerProxy() == null || !tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
            return null;
        } else {
            return tigerTestEnvMgr.getLocalTigerProxy().getBaseUrl();
        }
    }

    public static void waitForQuit() {
        // TODO TGR-516 wait for quit from workflow ui, then shutdown env and all processes and system exit to force mvn failsafe to abort ....
        envMgrApplicationContext.close();
        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofDays(1))
            .until(() -> !envMgrApplicationContext.isRunning());
    }

    private final static Pattern showSteps = Pattern.compile(".*TGR (zeige|show) ([\\w|ü|ß]*) (Banner|banner|text|Text) \"(.*)\"");//NOSONAR

    private static void assertThatTigerIsInitialized() {
        if (!initialized) {
            throw new TigerStartupException("Tiger test environment has not been initialized. "
                + "Did you call TigerDirector.beforeTestRun before starting test run?");
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
}
