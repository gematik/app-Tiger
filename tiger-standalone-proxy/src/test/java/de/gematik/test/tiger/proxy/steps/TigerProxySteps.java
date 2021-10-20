package de.gematik.test.tiger.proxy.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.pages.MainPage;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.net.ConnectException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.Config;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestInstance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class TigerProxySteps {

    private static final String XPATH_RES_BODY = "div[contains(@class, 'card full-width')";
    private static final String XPATH_REQUEST = "//div[@id='sidebar-menu']//div[contains(@class, 'menu-label')]";
    private static final String XPATH_RESPONSE = "//div[@id='sidebar-menu']//a[contains(@class, 'menu-label')]";
    private static final String CSS_ROUTESBLOCK = "#routeModalDialog > div.modal-content > article > div.message-body > div.routeListDiv.box";

    private static final String FILESUBSTRING = "tiger-report";

    MainPage mainPage = new MainPage();
    Actions action = new Actions(mainPage.getDriver());
    final File downloadFolder;

    public TigerProxySteps() {
        downloadFolder = Path.of(System.getProperty("user.home", "."), "Downloads").toFile();
        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs();
        }
        RestAssured.proxy("127.0.0.1", 6666);
    }

    @SneakyThrows
    public void openPage() {
        mainPage.openPage();
    }

    public void assertOnPage() {
        assertThat(mainPage.getDriver().getCurrentUrl()).startsWith(mainPage.getUrl());
    }

    public void clickOnRoutesButton() {
        actionHelper("//button[@id='routeModalBtn']");
    }

    public void removeExistingRoute() {
        WebElement removeButton = mainPage.getDriver()
            .findElement(By.xpath("//button[@class='button delete-route is-fullwidth is-danger']"));
        action.moveToElement(removeButton).click().perform();
    }

    public void assertRouteDeleted(String deletedFrom, String deletedTo) {
        WebDriverWait wait = new WebDriverWait(mainPage.getDriver(), 2);
        WebElement routesBlock = mainPage.getDriver().findElement(By.cssSelector(CSS_ROUTESBLOCK));

        wait.until(ExpectedConditions.textToBe(By.cssSelector(CSS_ROUTESBLOCK), "No Routes configured"));

        String routesList = routesBlock.getText();
        Assert.assertNotEquals(routesList, String.format("→ %s\n← %s", deletedFrom, deletedTo));
    }

    public void addNewRoute(String from, String to) {
        WebElement fromField = mainPage.getDriver().findElement(By.id("addNewRouteFromField"));
        WebElement toField = mainPage.getDriver().findElement(By.id("addNewRouteToField"));
        WebElement addNewRouteButton = mainPage.getDriver().findElement(By.id("addNewRouteBtn"));

        fromField.sendKeys(from);
        toField.sendKeys(to);

        action.moveToElement(addNewRouteButton).click().perform();
    }

    public void closeRouteDlg() {
        WebElement closeButton = mainPage.getDriver().findElement(By.xpath("//button[@class='delete']"));
        action.moveToElement(closeButton).click().perform();
    }

    public void assertSeeNewRoute(String from, String to) {
        WebDriverWait wait = new WebDriverWait(mainPage.getDriver(), 50);
        WebElement routesBlock = mainPage.getDriver().findElement(By.cssSelector(CSS_ROUTESBLOCK));
        wait.until(ExpectedConditions.textToBe(By.cssSelector(CSS_ROUTESBLOCK), String.format("→ %s\n← %s", from, to)));

        Assert.assertEquals(routesBlock.getText(), String.format("→ %s\n← %s", from, to));
    }

    public void sendRequest(String to, int responseCode) {
        Response response = RestAssured.get(to).andReturn();
        assertThat(response.getStatusCode()).isEqualTo(responseCode);
    }

    public void assertReportDisplayed() {
        checkRequest("1", "/test1.html");
        checkResponse("2", "200", "test1body");
        checkRequest("3", "/test2.html");
        checkResponse("4", "200", "test2body");
        checkFalseRequest();
    }

    public void clickOnSaveButton() {
        actionHelper("//button[@id='saveMsgs']");
    }

    public File assertReportDownloaded() throws InterruptedException {
        for (int second = 0; ; second++) {
            Thread.sleep(3000);
            if (second > 3) {
                Assert.fail("Timeout with report download");
            }
            try {
                List<File> fileList = Arrays.asList(downloadFolder.listFiles());
                List<File> updatedFileList = fileList
                    .stream()
                    .filter(file -> file.getName().contains(FILESUBSTRING))
                    .collect(Collectors.toList());
                Assert.assertEquals(updatedFileList.size(), 1);
                Assert.assertTrue("Report is contained within the Download folder",
                    updatedFileList.toString().contains(FILESUBSTRING));

                return updatedFileList.get(0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String openReport() throws IOException, InterruptedException {
        return IOUtils.toString(assertReportDownloaded().toURI(), StandardCharsets.UTF_8);
    }

    public void assertSeeMessageList() throws IOException, InterruptedException {
        Assert.assertTrue(openReport().replace("\n", "").matches(".*\\/test1.*\\/test2.*content-length: 36.*"));
    }

    public void clickOnResetButton() {
        actionHelper("//button[@id='resetMsgs']");
    }

    public void assertReportNotSeen() {
        assertElemTextMatches("//div[@id='sidebar-menu']", "");
        assertElemTextMatches("//div[contains(@class, 'msglist')]", "");
    }

    public void clickOnQuit() {
        WebElement quitProxyButton = mainPage.getDriver().findElement(By.cssSelector("#quitProxy"));
        // TODO Thomas Quit button is hidden right off the window, check responsiveness and why quit button is not line broken into next line
        ((JavascriptExecutor) mainPage.getDriver()).executeScript("arguments[0].click();", quitProxyButton);

        WebDriverWait wait = new WebDriverWait(mainPage.getDriver(), 5);
        Function<WebDriver, Boolean> quitProxyDisabled = driver -> !driver.findElement(By.cssSelector("#quitProxy"))
            .isEnabled();
        wait.until(quitProxyDisabled);
    }

    public void assertRequestsFail(String testUrl1) {
        Response response = null;
        try {
            response = RestAssured.get(testUrl1).andReturn();
        } catch (Exception cex) {
            log.warn("CEX Class " + cex.getClass().getName());
            if (cex instanceof ConnectException) {
                log.info("Connection refused - As EXPECTED");
                return;
            }
            throw new AssertionError("Unexpected exception while checking for request failure " + cex, cex);

        }
        assertThat(response).withFailMessage("Response is nULL!").isNotNull();
        assertThat(response.getStatusCode()).isNotEqualTo(200);
    }

    public void assertRequestTimesOut(String testUrl1) {
        Response response = null;
        try {
            response = RestAssured.get(testUrl1).andReturn();
        } catch (Exception cex) {
            log.warn("CEX Class " + cex.getClass().getName());
            if (cex instanceof ConnectException) {
                log.info("Connection refused - As EXPECTED");
                return;
            }
        }
        throw new RuntimeException("No timeout occured while sending request");
    }

    public void switchToUpdateMode(String updateId) {
        actionHelper("//label[@for='" + updateId + "']");
    }

    public void clickUpdate() {
        actionHelper("//button[@id='updateBtn']");
    }

    public void purgeDownloadFolder() {
        assertThat(downloadFolder.listFiles())
            .withFailMessage("Download folder " + downloadFolder.getAbsolutePath() + " does not exist!")
            .isNotNull();
        Arrays.stream(downloadFolder.listFiles())
            .filter(file -> file.getName().contains(FILESUBSTRING))
            .forEach(file -> {
                try {
                    Files.deleteIfExists(Paths.get(file.getAbsolutePath()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    public void switchToTestQuitMode() {
        JavascriptExecutor js = (JavascriptExecutor) mainPage.getDriver();
        js.executeScript("testActivateNoSystemExitOnQuit();");
    }


    public void checkRequest(String idx, String path) {
        assertElementExistsAndIsDisplayed(XPATH_REQUEST + "/span[text()='" + idx + "']");
        assertElemTextMatches(
            "//div[@id='sidebar-menu']//div[contains(@class, 'menu-label')]/span[text()='" + idx + "']/..",
            idx + "\nREQUEST");
        assertElementExistsAndIsDisplayed(
            XPATH_REQUEST + "/span[text()='" + idx + "']/../following-sibling::div[starts-with(text(), 'GET\n" + path
                + "')]");
    }

    public void checkFalseRequest() {
        assertElemTextDoesntMatch("#sidebar-menu > div",
            String.format("%s\n%s\n%s%s", "5", "REQUEST", "GET ", "/test"));
    }

    public void checkResponse(String idx, String statusCode, String resBodyMessage) {
        assertElemTextMatches(XPATH_RESPONSE + "/span[text()='" + idx + "']/..", idx + "\nRESPONSE");

        WebElement resBody = mainPage.getDriver().findElement(By.xpath(
            "//" + XPATH_RES_BODY + "]//span[text()='" + idx + "']//ancestor::"+XPATH_RES_BODY + "]//h1[text()='" + statusCode
                + "']//ancestor::"+XPATH_RES_BODY + "]//pre[@class='json' and contains(text(), '" + resBodyMessage + "')]"));
        Assert.assertTrue("The response body with given parameters is displayed on the page", resBody.isDisplayed());
    }

    public void assertElemTextMatches(String xpath, String text) {
        WebElement el = mainPage.elemX(xpath);
        Assert.assertEquals(text, el.getText());
    }

    public void assertElemTextDoesntMatch(String cssSelector, String text) {
        WebElement el = mainPage.getDriver().findElement(By.cssSelector(cssSelector));
        Assert.assertNotEquals(text, el.getText());
    }

    public void assertElementExistsAndIsDisplayed(String xpath) {
        WebElement el = mainPage.getDriver().findElement(By.xpath(xpath));
        if (!el.isDisplayed()) {
            throw new AssertionError("Element '" + xpath + "' is not displayed");
        }
    }

    public void actionHelper(String locatorStr) {
        WebElement el = mainPage.elemX(locatorStr);
        action.moveToElement(el).click().perform();
    }

    final AtomicReference<Process> proc = new AtomicReference<>();

    public void startWebServer() throws IOException {
//        - --control-port=19000

        if (proc.get() != null)  {
            stopWebServer();
        }

        var webRoot = Paths.get("src", "test", "resources", "testdata", "webroot").toFile();
        log.info("creating cmd line...");
        List<String> options = new ArrayList<>();
        options.add(findJavaExecutable());
        options.add("-jar");
        options.add("winstone.jar");
        options.add("--httpPort=10000");
        options.add("--webroot=" + webRoot.getAbsolutePath());
        RuntimeException throwing = null;
        try {
            final AtomicReference<Throwable> exception = new AtomicReference<>();
            var thread = new Thread(() -> {
                Process p = null;
                try {
                    p = new ProcessBuilder()
                        .command(options.toArray(String[]::new))
                        .directory(Paths.get("src", "test", "resources", "testdata").toFile())
                        .inheritIO()
                        .start();
                } catch (Throwable t) {
                    log.error("Failed to start process", t);
                    exception.set(t);
                }
                proc.set(p);
                log.info("Process set in atomic var " + p);
            });
            thread.start();

            await().atMost(10, TimeUnit.SECONDS).pollDelay(200, TimeUnit.MILLISECONDS)
                .until(() -> proc.get() != null || exception.get() != null);

            if (exception.get() != null) {
                throwing = new RuntimeException("Unable to start web server!", exception.get());
            }
        } finally {
            log.info("proc: " + proc.get());
            if (proc.get() != null) {
                if (proc.get().isAlive()) {
                    log.info("Started web server");
                } else if (proc.get().exitValue() == 0) {
                    log.info("Web server process exited already ");
                } else {
                    log.info("Unclear process state" + proc);
                    log.info(
                        "Output from cmd: " + IOUtils.toString(proc.get().getInputStream(), StandardCharsets.UTF_8));
                }
            } else {
                if (throwing == null) {
                    throwing = new RuntimeException("External Jar startup failed");
                } else {
                    throwing = new RuntimeException("External Jar startup failed", throwing);
                }
            }
        }
        if (throwing != null) {
            throw throwing;
        }
    }

    private String findJavaExecutable() {
        String[] paths = System.getenv("PATH").split(SystemUtils.IS_OS_WINDOWS ? ";" : ":");
        String javaProg = "java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
        return Arrays.stream(paths)
            .map(path -> Path.of(path, javaProg).toFile())
            .filter(file -> file.exists() && file.canExecute())
            .map(File::getAbsolutePath)
            .findAny()
            .orElseThrow(() -> new RuntimeException("Unable to find executable java program in PATH"));
    }

    public void stopWebServer() {
        if (proc.get() != null) {
            proc.get().destroy();
            proc.get().destroyForcibly();
            proc.set(null);
        }
    }
}

