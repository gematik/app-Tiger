/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.*;
import static org.awaitility.Awaitility.await;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.web.TigerBrowserUtil;
import de.gematik.test.tiger.lib.exception.TigerStartupException;
import de.gematik.test.tiger.lib.reports.TigerRestAssuredCurlLoggingFilter;
import de.gematik.test.tiger.lib.serenityrest.SerenityRestUtils;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgrApplication;
import de.gematik.test.tiger.testenvmgr.controller.TestExecutionController;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.env.ScenarioReplayer;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import io.cucumber.core.runtime.Runtime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
 *
 * <ul>
 *   <li>read and apply Tiger test framework configuration from tiger.yaml
 *   <li>start workflow UI, Tiger test environment manager and local Tiger Proxy
 * </ul>
 *
 * It also provides access to the Tiger test environment manager, the local Tiger Proxy and the
 * Workflow UI interface.
 */
@SuppressWarnings("unused") // API
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TigerDirector {

  @Getter private static TigerRestAssuredCurlLoggingFilter curlLoggingFilter;
  private static TigerTestEnvMgr tigerTestEnvMgr;
  private static boolean initialized = false;

  @Getter private static TigerLibConfig libConfig;
  private static ConfigurableApplicationContext envMgrApplicationContext;
  private static Runtime runtime;

  public static synchronized void start() {
    if (initialized) {
      log.info("Tiger Director already started, skipping");
      return;
    }
    try {
      showTigerBanner();
      readConfiguration();
      registerRestAssuredFilter();
      applyLoggingLevels();
      applyTestLibConfig();
    } catch (TigerConfigurationException tcex) {
      throw tcex;
    } catch (RuntimeException rte) {
      throw new TigerConfigurationException(
          "Unable to read/process configuration - " + rte.getMessage(), rte);
    }
    try {
      // get free port
      startTestEnvMgr();
      setupScenarioReplayer(runtime);
      startWorkflowUi();
      setupTestEnvironment(Optional.of(LocalProxyRbelMessageListener.rbelMessageListener));
      setDefaultProxyToLocalTigerProxy();
    } catch (RuntimeException e) {
      quit(true);
      throw e;
    }

    initialized = true;
  }

  private static void setupScenarioReplayer(Runtime runtime) {
    ScenarioReplayer scenarioReplayer = envMgrApplicationContext.getBean(ScenarioReplayer.class);
    scenarioReplayer.setRuntime(runtime);
  }

  public static synchronized void startStandaloneTestEnvironment() {
    log.info("Starting Tiger testenvironment in STANDALONE MODE!");
    if (initialized) {
      log.info("Tiger Director already started, skipping");
      return;
    }
    try {
      showTigerBanner();
      readConfiguration();
      if (getLibConfig().isActivateWorkflowUi()) {
        log.warn(
            "Starting WorkflowUI in standalone mode is not supported, deactivating the flag in"
                + " config");
        getLibConfig().activateWorkflowUi = false;
      }
      applyLoggingLevels();
      applyTestLibConfig();
    } catch (RuntimeException rte) {
      throw new TigerConfigurationException(
          "Unable to read/process configuration - " + rte.getMessage(), rte);
    }
    try {
      // get free port
      startTestEnvMgr();
      if (tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
        log.warn(
            "Starting local Tiger Proxy in standalone mode is not supported, deactivating the flag"
                + " in config");
        tigerTestEnvMgr.getConfiguration().setLocalProxyActive(false);
      }
      setupTestEnvironment(Optional.of(LocalProxyRbelMessageListener.rbelMessageListener));
    } catch (RuntimeException e) {
      quit(true);
      throw e;
    }
    initialized = true;
  }

  private static void applyLoggingLevels() {
    try {
      TigerGlobalConfiguration.readMapWithCaseSensitiveKeys("tiger", "logging", "level")
          .forEach(TigerServerLogManager::setLoggingLevel);
    } catch (NoClassDefFoundError ncde) {
      log.warn("Unable to detect logback library! Setting log level feature not supported");
    }
    // setLoggingLevel is sufficient for almost all cases. SpringBoot applications are a special
    // case -
    // SpringBoot resets the levels during startup.
    // So When using SpringBootApplicationBuilder the respective properties have to be passed in
    // manually!
    // except of course for the main methods of the SpringBottApplication classes as there we expect
    // to use application.yaml
  }

  private static boolean shutdownHookRegistered = false;

  public static synchronized void registerShutdownHook() {
    if (shutdownHookRegistered) {
      return;
    }
    shutdownHookRegistered = true;

    log.info("Registering shutdown hook...");
    java.lang.Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (tigerTestEnvMgr == null) {
                    log.info(
                        Ansi.colorize(
                            "Finished shutdown (no test environment) OK", RbelAnsiColors.RED_BOLD));
                    return;
                  }

                  if (!tigerTestEnvMgr.isShuttingDown()) {
                    quit(true);
                  }
                }));
  }

  public static void waitForAcknowledgedQuit() {
    quit(true);
  }

  private static void quit(boolean shouldUserAcknowledgeShutdown) {
    try {
      if (getLibConfig() != null
          && getLibConfig().isActivateWorkflowUi()
          && shouldUserAcknowledgeShutdown) {
        // This method is called in the shut down hook of the JVM and we experienced that using the
        // logger
        // sometimes kept this message from appearing in the console, so resorting to System.out
        // here
        System.out.println( // NOSONAR
            Ansi.colorize(
                "TGR Workflow UI is active, please press quit in browser window...",
                RbelAnsiColors.GREEN_BOLD));
        if (tigerTestEnvMgr != null) {
          tigerTestEnvMgr.receiveTestEnvUpdate(
              TigerStatusUpdate.builder()
                  .bannerMessage("Test run finished, press SHUTDOWN")
                  .bannerColor("green")
                  .bannerType(BannerType.TESTRUN_ENDED)
                  .build());
          try {
            await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
                .until(
                    () ->
                        tigerTestEnvMgr.isUserAcknowledgedOnWorkflowUi()
                            || tigerTestEnvMgr.isShouldAbortTestExecution());
          } finally {
            tigerTestEnvMgr.shutDown();
          }
        }
      } else if (tigerTestEnvMgr != null) {
        tigerTestEnvMgr.receivedConfirmationFromWorkflowUi(false);
        System.out.println("TGR Shutting down test env..."); // NOSONAR
        tigerTestEnvMgr.shutDown();
      }
    } finally {
      unregisterRestAssuredFilter();
      System.out.println("TGR Destroying spring boot context after testrun..."); // NOSONAR
      if (envMgrApplicationContext != null) {
        envMgrApplicationContext.close();
      }
      System.out.println("TGR Tiger shut down orderly"); // NOSONAR
    }
  }

  private static void setupTestEnvironment(
      Optional<IRbelMessageListener> tigerProxyMessageListener) {
    if (SKIP_ENVIRONMENT_SETUP.getValueOrDefault().equals(Boolean.FALSE)) {
      log.info(
          "\n" + Banner.toBannerStr("SETTING UP TESTENV...", RbelAnsiColors.BLUE_BOLD.toString()));
      tigerTestEnvMgr.setUpEnvironment(tigerProxyMessageListener);
      log.info("\n" + Banner.toBannerStr("TESTENV SET UP OK", RbelAnsiColors.BLUE_BOLD.toString()));
    }
  }

  public static synchronized void readConfiguration() {
    libConfig =
        TigerGlobalConfiguration.instantiateConfigurationBeanStrict(
                TigerLibConfig.class, "TIGER_LIB")
            .orElseGet(TigerLibConfig::new);
  }

  private static void showTigerBanner() {
    // created via https://kirilllive.github.io/ASCII_Art_Paint/ascii_paint.html
    if (SHOW_TIGER_LOGO.getValueOrDefault().equals(Boolean.TRUE)) {
      try {
        log.info(
            "\n"
                + IOUtils.toString(
                    Objects.requireNonNull(
                        TigerDirector.class.getResourceAsStream("/tiger2-logo.ansi")),
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
      RbelAnsiColors.activateAnsiColors();
    } else {
      RbelAnsiColors.deactivateAnsiColors();
    }
  }

  private static synchronized void startTestEnvMgr() {
    log.info(
        "\n" + Banner.toBannerStr("STARTING TESTENV MGR...", RbelAnsiColors.BLUE_BOLD.toString()));

    Map<String, Object> properties = TigerTestEnvMgr.getConfiguredLoggingLevels();
    properties.put("server.port", TESTENV_MGR_RESERVED_PORT.getValueOrDefault());
    envMgrApplicationContext =
        new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(properties)
            .sources(TigerTestEnvMgrApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

    tigerTestEnvMgr = envMgrApplicationContext.getBean(TigerTestEnvMgr.class);
    TestExecutionController testExecutionController =
        envMgrApplicationContext.getBean(TestExecutionController.class);

    testExecutionController.setShutdownListener(
        () -> {
          await().pollDelay(300, TimeUnit.MILLISECONDS).until(() -> true);
          tigerTestEnvMgr.abortTestExecution();
          quit(false);
        });

    testExecutionController.setPauseListener(
        () -> SerenityReporterCallbacks.setPauseMode(!SerenityReporterCallbacks.isPauseMode()));
  }

  private static synchronized void startWorkflowUi() {
    if (libConfig.activateWorkflowUi) {
      log.info(
          "\n"
              + Banner.toBannerStr(
                  "STARTING WORKFLOW UI ...", RbelAnsiColors.BLUE_BOLD.toString()));
      if (libConfig.startBrowser) {
        TigerBrowserUtil.openUrlInBrowser(
            "http://localhost:"
                + TESTENV_MGR_RESERVED_PORT
                    .getValue()
                    .orElseThrow(
                        () -> new TigerEnvironmentStartupException("Failed to start browser!"))
                    .toString(),
            "Workflow UI");
      }
      log.info("Waiting for workflow Ui to fetch status...");
      try {
        int duration = 10;
        if (!libConfig.startBrowser) {
          log.info(
              "Workflow UI http://localhost:" + TESTENV_MGR_RESERVED_PORT.getValue().orElseThrow());
          duration = 60;
        }
        await()
            .atMost(Duration.ofSeconds(duration))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> tigerTestEnvMgr.isWorkflowUiSentFetch());

      } catch (ConditionTimeoutException cte) {
        libConfig.activateWorkflowUi = false;
        throw new TigerTestEnvException("No feedback from workflow Ui, aborting!", cte);
      }
      try {
        TimeUnit.MILLISECONDS.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new TigerTestEnvException(
            "Interrupt received while waiting for workflow Ui to become ready", e);
      }
    }
  }

  private static synchronized void setDefaultProxyToLocalTigerProxy() {
    TigerProxyConfiguration tpCfg = tigerTestEnvMgr.getConfiguration().getTigerProxy();
    // set proxy to local tiger proxy for test suites
    if (tigerTestEnvMgr.isLocalTigerProxyActive()) {
      tigerTestEnvMgr.getLocalTigerProxyOptional().ifPresent(SerenityRestUtils::setupSerenityRest);
      tigerTestEnvMgr.setDefaultProxyToLocalTigerProxy();
    } else {
      log.info(
          Ansi.colorize(
              "SKIPPING TIGER PROXY settings as localProxyActive==false...",
              RbelAnsiColors.RED_BOLD));
    }
  }

  public static synchronized boolean isInitialized() {
    return initialized;
  }

  public static TigerTestEnvMgr getTigerTestEnvMgr() {
    assertThatTigerIsInitialized();
    return tigerTestEnvMgr;
  }

  @SuppressWarnings("UnusedReturnValue") // API method
  public static String getLocalTigerProxyUrl() {
    assertThatTigerIsInitialized();
    if (tigerTestEnvMgr.getLocalTigerProxyOptional().isEmpty()
        || !tigerTestEnvMgr.getConfiguration().isLocalProxyActive()) {
      return null;
    } else {
      return tigerTestEnvMgr.getLocalTigerProxyOrFail().getBaseUrl();
    }
  }

  public static void assertThatTigerIsInitialized() {
    if (!initialized) {
      throw new TigerStartupException(
          "Tiger test environment has not been initialized successfully!");
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
            "Trying to use Serenity functionality, but Serenity BDD packages are not declared as"
                + " runtime dependency.",
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
    pauseExecution("", false);
  }

  public static void pauseExecution(String message, boolean isHtml) {
    String defaultMessage = "Test execution paused, click to continue";
    if (StringUtils.isBlank(message)) {
      message = defaultMessage;
    }

    if (getLibConfig().isActivateWorkflowUi()) {
      tigerTestEnvMgr.receiveTestEnvUpdate(
          TigerStatusUpdate.builder()
              .bannerMessage(message)
              .bannerColor("green")
              .bannerType(BannerType.STEP_WAIT)
              .bannerIsHtml(isHtml)
              .build());
      await()
          .pollInterval(1, TimeUnit.SECONDS)
          .atMost(getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
          .until(() -> tigerTestEnvMgr.isUserAcknowledgedOnWorkflowUi());
      tigerTestEnvMgr.resetConfirmationFromWorkflowUi();
    } else {
      throw new TigerTestEnvException(
          "The step 'TGR pause test run execution with message \"{}\"' is not supported "
              + "outside the Workflow UI. Please check the manual for more information.",
          message);
    }
  }

  public static void pauseExecution(String message) {
    pauseExecution(message, false);
  }

  public static void pauseExecutionAndFailIfDesired(String message, String errorMessage) {
    if (getLibConfig().isActivateWorkflowUi()) {
      tigerTestEnvMgr.receiveTestEnvUpdate(
          TigerStatusUpdate.builder()
              .bannerMessage(message)
              .bannerColor("black")
              .bannerType(BannerType.FAIL_PASS)
              .build());
      await()
          .pollInterval(1, TimeUnit.SECONDS)
          .atMost(getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
          .until(() -> tigerTestEnvMgr.isUserAcknowledgedOnWorkflowUi());
      tigerTestEnvMgr.resetConfirmationFromWorkflowUi();
      if (tigerTestEnvMgr.isUserPressedFailTestExecution()) {
        Fail.fail(errorMessage);
      }
    } else {
      throw new TigerTestEnvException(
          "The step 'TGR pause test run execution with message \"{}\" and "
              + "message in case of error \"{}\"' is not supported outside the Workflow UI. "
              + "Please check the manual for more information.",
          message,
          errorMessage);
    }
  }

  public static void registerRuntime(Runtime runtime) {
    TigerDirector.runtime = runtime;
  }

  public static Optional<Runtime> loadRuntime() {
    return Optional.ofNullable(TigerDirector.runtime);
  }
}
