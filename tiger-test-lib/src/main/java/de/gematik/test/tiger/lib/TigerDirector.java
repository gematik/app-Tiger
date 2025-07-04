/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.lib;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.*;
import static org.awaitility.Awaitility.await;

import com.google.common.annotations.VisibleForTesting;
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
import de.gematik.test.tiger.lib.rbel.RbelMessageRetriever;
import de.gematik.test.tiger.lib.reports.TigerRestAssuredCurlLoggingFilter;
import de.gematik.test.tiger.lib.serenityrest.SerenityRestUtils;
import de.gematik.test.tiger.lib.shutdown.ShutdownReason;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgrApplication;
import de.gematik.test.tiger.testenvmgr.controller.TestExecutionController;
import de.gematik.test.tiger.testenvmgr.data.BannerType;
import de.gematik.test.tiger.testenvmgr.env.ScenarioRunner;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.core.plugin.report.SerenityReporterCallbacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
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

  public static void main(String[] args) {
    start();
  }

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
      startWorkflowUi();
      setupTestEnvironment();
      setDefaultProxyToLocalTigerProxy();
      initialized = true;
      if (getTigerTestEnvMgr().isLocalTigerProxyActive()) {
        LocalProxyRbelMessageListener.initialize();
        if (libConfig.clearEnvironmentStartupTraffic) {
          LocalProxyRbelMessageListener.getInstance().clearAllMessages();
        }
      }
    } catch (RuntimeException e) {
      initialized = false;
      quit(new ShutdownReason("Start up of Test environment failed!", true, e));
      throw e;
    }
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
            "Starting Workflow UI in standalone mode is not supported, deactivating the flag in"
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
      setupTestEnvironment();
      initialized = true;
    } catch (RuntimeException e) {
      initialized = false;
      quit(new ShutdownReason("Start up of Test environment failed!", true, e));
      throw e;
    }
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
                    quit(ShutdownReason.REGULAR_SHUTDOWN_USER_ACKNOWLEDGE);
                  }
                }));
  }

  public static void waitForAcknowledgedQuit() {
    quit(ShutdownReason.REGULAR_SHUTDOWN_USER_ACKNOWLEDGE);
  }

  private static void quit(ShutdownReason shutdownReason) {
    try {
      if (getLibConfig() != null
          && getLibConfig().isActivateWorkflowUi()
          && shutdownReason.isShouldUserAcknowledgeShutdown()) {
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
                  .bannerMessage(shutdownReason.getMessage())
                  .bannerColor(shutdownReason.getException().map(e -> "red").orElse("green"))
                  .bannerDetails(
                      shutdownReason
                          .getException()
                          .map(TigerStatusUpdate.BannerDetails::new)
                          .orElse(null))
                  .bannerType(BannerType.TESTRUN_ENDED)
                  .build());
          try {
            await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(getLibConfig().getPauseExecutionTimeoutSeconds(), TimeUnit.SECONDS)
                .until(
                    () ->
                        tigerTestEnvMgr.getUserConfirmQuit().get()
                            || tigerTestEnvMgr.isShouldAbortTestExecution());
          } finally {
            tigerTestEnvMgr.shutDown();
          }
        }
      } else if (tigerTestEnvMgr != null) {
        tigerTestEnvMgr.receivedQuitConfirmationFromWorkflowUi();
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

  private static void setupTestEnvironment() {
    if (SKIP_ENVIRONMENT_SETUP.getValueOrDefault().equals(Boolean.FALSE)) {
      log.info(
          "\n" + Banner.toBannerStr("SETTING UP TESTENV...", RbelAnsiColors.BLUE_BOLD.toString()));
      tigerTestEnvMgr.setUpEnvironment();
      log.info("\n" + Banner.toBannerStr("TESTENV SET UP OK", RbelAnsiColors.BLUE_BOLD.toString()));
    }
  }

  public static synchronized void readConfiguration() {
    libConfig =
        TigerGlobalConfiguration.instantiateConfigurationBean(TigerLibConfig.class, "TIGER_LIB")
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
    properties.put("spring.mustache.enabled", false); // TGR-875 avoid warning in console
    properties.put("spring.mustache.check-template-location", false);
    properties.putAll(TigerTestEnvMgr.getTigerLibConfiguration());

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
          quit(ShutdownReason.REGULAR_SHUTDOWN_NO_ACKNOWLEDGE);
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
          duration = 120;
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
      log.error("\n" + Banner.toBannerStrWithCOLOR("Test env not initialized!", "red"));
      log.error(
          Banner.toTextStr("Did you use TigerCucumberRunner to execute the test run?", "yellow"));
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

  @VisibleForTesting
  @SneakyThrows
  public static synchronized void testUninitialize() {
    initialized = false;
    tigerTestEnvMgr = null;
    curlLoggingFilter = null;

    RbelMessageRetriever.clearInstance(); // NOSONAR - this is only called from test code

    LocalProxyRbelMessageListener
        .clearTestingInstance(); // NOSONAR - the method testUninitialize should also only be used
    // for testing

    System.clearProperty("TIGER_TESTENV_CFGFILE");
    System.clearProperty("http.proxyHost");
    System.clearProperty("https.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyPort");

    ScenarioRunner.clearScenarios();
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
          .until(TigerDirector::hasUserAcknowledgedDialog);
    } else {
      throw new TigerTestEnvException(
          "The step 'TGR pause test run execution with message \"{}\"' is not supported "
              + "outside the Workflow UI. Please check the manual for more information.",
          message);
    }
  }

  private static boolean hasUserAcknowledgedDialog() {
    return tigerTestEnvMgr.isShuttingDown()
        || tigerTestEnvMgr.getUserAcknowledgedOnWorkflowUi().compareAndSet(true, false);
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
          .until(TigerDirector::hasUserAcknowledgedDialog);
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
}
