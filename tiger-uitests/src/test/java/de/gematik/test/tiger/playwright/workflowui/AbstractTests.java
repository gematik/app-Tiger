/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.playwright.workflowui;

import static org.awaitility.Awaitility.await;
import com.microsoft.playwright.Browser;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * This class reads the workflow ui port out from the log file that mvn creates when executing tiger and the feature
 * file for the playwright tests.
 * local test setup:
 *  in the first terminal do (this will start the tiger and workflow ui but without starting the browser):
 *          cd tiger-uitests
 *          rm -f mvn-playwright-log.txt
 *          mvn -P start-tiger-dummy verify | tee mvn-playwright-log.txt
 *
 *  in the second termin do (this will start the actual playwright tests):
 *          cd tiger-uitests
 *          mvn -P run-playwright-test verify
 *
 * See tiger-uitests-playwright-tests.Jenkinsfile for further information.
 * It also holds the variables used by the playwright tests such as playwright, browser and page.
 */
@Slf4j
public class AbstractTests {

    static String port;

    private static void checkPort() {
        if (port != null && !port.isEmpty()) {
            return;
        }
        Path path = Paths.get("mvn-playwright-log.txt");
        await().pollInterval(1, TimeUnit.SECONDS).atMost(60, TimeUnit.SECONDS).until(() -> {
                if (Files.exists(path)) {
                    FileInputStream fis = new FileInputStream(path.toString());
                    await().pollInterval(500, TimeUnit.MILLISECONDS).atMost(60, TimeUnit.SECONDS).until(() ->
                        getPort(fis) != null);
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
    static void launchBrowser() {
        checkPort();
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        page = browser.newPage();
        page.navigate("http://localhost:" + port);
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @AfterEach
    void setBackToNormalState() {
        //check if sidebar is closed
        if (page.querySelector("#test-sidebar-title").isVisible()) {
            page.querySelector("#test-tiger-logo").click();
        }
        page.querySelector("#test-execution-pane-tab").click();
        //check if webslider is closed
        if (page.locator("#test-rbel-logo").isVisible()) {
            page.locator("#test-webui-slider").click();
        }
    }
}
