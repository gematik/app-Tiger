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
package de.gematik.test.tiger.playwright.workflowui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * This class reads the workflow ui port out from the log file that mvn creates when executing tiger
 * and the feature file for the playwright tests. local test setup: first compile test classes
 *
 * <p>cd tiger-uitests
 *
 * <p>mvn test-compile -P start-tiger-dummy
 *
 * <p>in the first terminal run the following command (this will start the tiger and workflow ui but
 * without starting the browser):
 *
 * <p>cd tiger-uitests
 *
 * <p>./startWorkflowUi.sh
 *
 * <p>This script, located at tiger-uitests/startWorkflowUi.sh, sets environment variables and
 * properties specifically for the Tiger Global Configuration Editor tests.
 *
 * <p>in the second terminal do (this will start the actual playwright tests):
 *
 * <p>cd tiger-uitests
 *
 * <p>mvn --no-transfer-progress -P run-playwright-test failsafe:integration-test failsafe:verify
 *
 * <p>See tiger-uitests-playwright-tests.Jenkinsfile for further information. It also holds the
 * variables used by the playwright tests such as playwright, browser and page.
 */
@Slf4j
@SuppressWarnings("java:S2187")
@ExtendWith(AbstractBase.SaveArtifactsOnTestFailed.class)
public class AbstractBase implements ExtensionContext.Store.CloseableResource {

  static String port;
  private static final String doc = "doc";
  private static final String user_manual = "user_manual";
  private static final String screenshots = "screenshots";
  protected static final int NUMBER_OF_FEATURES = 2;
  protected static final String[][] SCENARIO_DATA_WITH_OUTLINE_NODES = {
    {"Simple Get Request", "0"},
    {"Get Request to folder", "0"},
    {"PUT Request to folder", "0"},
    {"PUT Request with body to folder", "0"},
    {"PUT Request with body from file to folder", "0"},
    {"DELETE Request without body shall fail", "0"},
    {"Request with custom header", "0"},
    {"Request with default header", "0"},
    {"Request with custom and default header", "0"},
    {"Request with DataTables Test", "0"},
    {"Request with custom and default header", "0"},
    {"Test red with Dagmar", "0"}, // Scenario Outline parent node
    {"Test red with Dagmar", "1"},
    {"Test blue with Nils", "2"},
    {"Test green with Tim", "3"},
    {"Test yellow with Sophie", "4"},
    {"Test green with foo again", "0"}, // Scenario Outline parent node
    {"Test green with foo again", "1"},
    {"Test red with bar again", "2"},
    {"Test Find Last Request", "0"},
    {"Test find last request with parameters", "0"},
    {"Test find last request", "0"},
    {"JEXL Rbel Namespace Test", "0"}, // Scenario Outline parent node
    {"JEXL Rbel Namespace Test", "1"},
    {"JEXL Rbel Namespace Test", "2"},
    {"JEXL Rbel Namespace Test", "3"},
    {"JEXL Rbel Namespace Test", "4"},
    {"JEXL Rbel Namespace Test", "5"},
    {"Request a non existing url", "0"},
    {"Request for testing tooltips", "0"},
    {"A scenario with substeps", "0"},
    {"Test zeige HTML", "0"}
  };

  protected static final String[][] SCENARIO_DATA_NO_OUTLINE =
      Arrays.stream(SCENARIO_DATA_WITH_OUTLINE_NODES)
          .filter(
              arr ->
                  !(arr[0].equals("Test red with Dagmar") && arr[1].equals("0")
                      || arr[0].equals("Test green with foo again") && arr[1].equals("0")
                      || arr[0].equals("JEXL Rbel Namespace Test") && arr[1].equals("0")))
          .toArray(String[][]::new);

  protected static final int NUMBER_OF_SCENARIOS_INCLUDING_OUTLINE_NODES =
      SCENARIO_DATA_WITH_OUTLINE_NODES.length;
  protected static final int NUMBER_OF_SCENARIOS = SCENARIO_DATA_NO_OUTLINE.length;
  protected static final int TOTAL_MESSAGES = 60;
  protected static final int MESSAGES_PER_PAGE = 20;
  protected static final int TOTAL_PAGES =
      (int) Math.ceil(TOTAL_MESSAGES / (MESSAGES_PER_PAGE * 1.0));
  private static BrowserContext context;

  private static final boolean tracingEnabled =
      Boolean.parseBoolean(System.getProperty("tiger.test.tracing", "true"));

  private static void checkPort() {
    if (port != null && !port.isEmpty()) {
      return;
    }
    Path path = Paths.get("mvn-playwright-log.txt");
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(
            () -> {
              if (Files.exists(path)) {
                try (FileInputStream fis = new FileInputStream(path.toString())) {
                  await()
                      .pollInterval(200, TimeUnit.MILLISECONDS)
                      .atMost(30, TimeUnit.SECONDS)
                      .until(() -> getPort(fis) != null);
                }
                return true;
              } else {
                return false;
              }
            });
  }

  private static String getPort(FileInputStream fis) {
    try {
      String content = IOUtils.toString(fis, StandardCharsets.UTF_8);
      String[] lines = content.split("\n");
      for (String line : lines) {
        if (line.contains("Workflow UI http://localhost:")) {
          port = line.substring(line.indexOf("Workflow UI http://localhost:") + 29).trim();
          log.info("BrowserPort:" + port);
          return port;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  // Shared between all tests in this class.
  protected static Playwright playwright;
  protected static Browser browser;
  protected static Page page;

  @BeforeAll
  static synchronized void launchBrowser() throws IOException {
    if (playwright != null) {
      activateTracing();
      return;
    }
    checkPort();
    playwright = Playwright.create();
    log.info("Playwright created");
    // if you are running tests locally and want to troubleshoot, then set this to false and change
    // the browser
    // from .firefox() to .chromium().
    // Then go to the test you are debugging and add a page.pause() call.
    boolean runHeadless = Boolean.parseBoolean(System.getProperty("tiger.test.headless", "true"));
    browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(runHeadless));

    context = browser.newContext();
    activateTracing();
    log.info("Browser launched at http://localhost:{}", port);
    page = context.newPage();
    page.setDefaultTimeout(60000D);
    page.setDefaultNavigationTimeout(60000D);
    page.navigate("http://localhost:" + port);

    File artefactsFolder =
        Path.of(System.getProperty("buildDirectory", "target"), "playwright-artifacts").toFile();
    FileUtils.deleteDirectory(artefactsFolder);
    FileUtils.forceMkdir(artefactsFolder);
  }

  private static void activateTracing() {
    if (tracingEnabled && context != null) {
      log.info("Activating tracing...");
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
    }
  }

  @BeforeEach
  void logMethodName(TestInfo testInfo) {
    log.info("Running test: {}", testInfo.getDisplayName());
  }

  @AfterAll
  static synchronized void saveTracing(TestInfo testInfo) {
    String clzName =
        testInfo
            .getTestClass()
            .orElseGet(
                () -> {
                  return UnknownError.class;
                })
            .getName();
    if (tracingEnabled && context.tracing() != null) {
      log.info("Saving playwright trace archive for {}...", clzName);
      context
          .tracing()
          .stop(
              new Tracing.StopOptions()
                  .setPath(
                      Paths.get(
                          System.getProperty("buildDirectory", "target"),
                          "playwright-artifacts",
                          "trace-" + clzName + ".zip")));
    }
  }

  protected void setBackToNormalState() {
    // check if sidebar is closed
    if (page.querySelector("#test-sidebar-title").isVisible()) {
      page.querySelector("#test-tiger-logo").click();
    }
    page.querySelector("#test-execution-pane-tab").click();
    // check if webslider is closed
    if (page.locator("#test-rbel-logo").isVisible()) {
      page.locator("#test-webui-slider").click();
    }
  }

  protected Path getPath(String file) {
    return Paths.get("..", doc, user_manual, screenshots, file);
  }

  protected void screenshotWithHighlightedElementById(
      Page page, String fileName, String elementId) {
    doActionWithHighlightedElement(
        page.locator("#".concat(elementId)), () -> screenshot(page, fileName));
  }

  protected void doActionWithHighlightedElement(Locator locator, Runnable action) {
    locator.evaluate("e => e.style.setProperty('background-color', 'yellow', 'important')");
    // Wait for the browser to repaint so the highlight is visible in screenshots
    page.waitForTimeout(400);
    action.run();
    locator.evaluate("e => e.style.removeProperty(\"background-color\")");
  }

  protected void screenshotOnlyElementById(Page page, String fileName, String elementId) {
    screenshotElement(page.locator("#".concat(elementId)), fileName);
  }

  protected void screenshotOnlyElementById(
      Page page, String fileName, String elementId, String highlightSelector) {
    screenshotElement(page.locator("#".concat(elementId)), fileName, highlightSelector);
  }

  protected void screenshotWithHighlightedByClassname(
      Page page, String fileName, String classname) {
    var classnameSelector =
        Arrays.stream(classname.split(" ")).map("."::concat).collect(Collectors.joining());
    doActionWithHighlightedElement(
        page.locator(classnameSelector).first(), () -> screenshot(page, fileName));
  }

  protected void screenshotOnlyElementByClassname(Page page, String fileName, String classname) {
    screenshotElement(page.locator(".".concat(classname)), fileName);
  }

  protected void screenshotOnlyElementByClassname(
      Page page, String fileName, String classname, String highlightSelector) {
    var classnameSelector =
        Arrays.stream(classname.split(" ")).map("."::concat).collect(Collectors.joining());
    screenshotElement(page.locator(classnameSelector), fileName, highlightSelector);
  }

  protected void screenshot(Page page, String fileName) {
    await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
    page.screenshot(new Page.ScreenshotOptions().setFullPage(false).setPath(getPath(fileName)));
  }

  protected void screenshotElement(Locator element, String fileName) {
    element.screenshot(new Locator.ScreenshotOptions().setPath(getPath(fileName)));
  }

  protected void screenshotElement(Locator element, String fileName, String highlightSelector) {
    var elementToHighlight = element.locator(highlightSelector).first();
    doActionWithHighlightedElement(elementToHighlight, () -> screenshotElement(element, fileName));
  }

  protected void screenshotHighlightedElement(Locator container, Locator element, String fileName) {
    doActionWithHighlightedElement(element, () -> screenshotElement(container, fileName));
  }

  protected void screenshotPageWithHighlightedElement(Locator elementToHighlight, String fileName) {
    doActionWithHighlightedElement(elementToHighlight, () -> screenshot(page, fileName));
  }

  protected void openSidebar() {
    int ctr = 0;
    while (ctr < 3 && page.locator(".test-sidebar-collapsed").isVisible()) {
      page.querySelector("#test-tiger-logo").click();
      try {
        await()
            .atMost(Duration.ofSeconds(5L))
            .until(page.locator("#test-sidebar-feature")::isVisible);
      } catch (ConditionTimeoutException cte) {
        try {
          Files.createDirectories(Path.of("./target/playwright-artifacts"));
          screenshot(
              page,
              "./target/playwright-artifacts/OpenSideBarFailed"
                  + ctr
                  + "-"
                  + UUID.randomUUID()
                  + ".png");
        } catch (IOException e) {
          log.error("Unable to save screenshot while failing to open sidebar", e);
        }
        ctr++;
      }
    }
    assertThat(page.locator(".test-sidebar-open")).isVisible();
  }

  protected void closeSidebar() {
    if (page.locator(".test-sidebar-open").isVisible()) {
      page.querySelector("#test-tiger-logo").click();
    }
  }

  @Override
  public void close() {
    log.info("Closing playwright...");
    context.close();
    browser.close();
    playwright.close();
  }

  protected static class SaveArtifactsOnTestFailed implements TestWatcher {

    // defined in the config of maven-failsafe-plugin in pom.xml
    private final String buildDirectory = System.getProperty("buildDirectory", "target");

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
      log.error("FAILED");
      AbstractBase testInstance = getTestInstance(context);
      String fileName = getFileName(context);
      saveScreenshot(fileName);
      testInstance.setBackToNormalState();
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
      log.info("PASS");
      AbstractBase testInstance = getTestInstance(context);
      testInstance.setBackToNormalState();
    }

    private AbstractBase getTestInstance(ExtensionContext context) {
      return (AbstractBase) context.getRequiredTestInstance();
    }

    private String getFileName(ExtensionContext context) {
      return String.format(
          "%s.%s-%s",
          context.getRequiredTestClass().getSimpleName(),
          context.getRequiredTestMethod().getName(),
          new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss").format(new Date()));
    }

    private void saveScreenshot(String fileName) {
      try {
        log.info("Saving screenie for failed test in {}", fileName);
        byte[] screenshot = page.screenshot();
        String directory = "playwright-artifacts";
        Files.write(Paths.get(buildDirectory, directory, fileName + ".png"), screenshot);
        String html = page.innerHTML("html");
        Files.writeString(Paths.get(buildDirectory, directory, fileName + ".html"), html);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
