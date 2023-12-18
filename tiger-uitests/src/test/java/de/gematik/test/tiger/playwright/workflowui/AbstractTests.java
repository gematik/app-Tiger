/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;

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
public class AbstractTests implements ExtensionContext.Store.CloseableResource {

  static String port;
  private static final String doc = "doc";
  private static final String user_manual = "user_manual";
  private static final String screenshots = "screenshots";

  private static void checkPort() {
    if (port != null && !port.isEmpty()) {
      return;
    }
    Path path = Paths.get("mvn-playwright-log.txt");
    await()
        .pollInterval(1, TimeUnit.SECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(
            () -> {
              if (Files.exists(path)) {
                FileInputStream fis = new FileInputStream(path.toString());
                await()
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .atMost(30, TimeUnit.SECONDS)
                    .until(() -> getPort(fis) != null);
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
          log.info("BrowserPort:" + port + "");
          return port;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  // Shared between all tests in this class.
  static Playwright playwright;
  static Browser browser;
  static Page page;

  @BeforeAll
  static synchronized void launchBrowser() {
    if (playwright != null) {
      return;
    }
    checkPort();
    playwright = Playwright.create();
    log.info("Playwright created");
    browser = playwright.chromium().launch(new LaunchOptions().setHeadless(true));
    log.info("Browser launched");
    page = browser.newPage();
    log.info("new page");
    page.navigate("http://localhost:" + port);
    log.info("to http://localhost:" + port + " navigated");
  }

  @AfterEach
  void setBackToNormalState() {
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

  Path getPath(String file) {
    return Paths.get("..", doc, user_manual, screenshots, file);
  }

  void screenshot(Page page, String fileName, String attribute, boolean isElement) {
    if (isElement) {
      page.evaluate(
          "document.getElementById(\"" + attribute + "\").style.backgroundColor='yellow'");
    } else {
      page.evaluate(
          "document.getElementsByClassName(\""
              + attribute
              + "\")[0].style.backgroundColor='yellow'");
    }
    page.screenshot(new Page.ScreenshotOptions().setFullPage(false).setPath(getPath(fileName)));
    if (isElement) {
      page.evaluate(
          "document.getElementById(\""
              + attribute
              + "\").style.removeProperty(\"background-color\")");
    } else {
      page.evaluate(
          "document.getElementsByClassName(\""
              + attribute
              + "\")[0].style.removeProperty(\"background-color\")");
    }
  }

  void screenshot(Page page, String fileName) {
    page.screenshot(new Page.ScreenshotOptions().setFullPage(false).setPath(getPath(fileName)));
  }

  @Override
  public void close() throws Throwable {
    browser.close();
    playwright.close();
  }
}
