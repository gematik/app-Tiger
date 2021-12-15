package de.gematik.test.tiger.proxy.ui.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import de.gematik.test.tiger.proxy.ui.UiTest;
import de.gematik.test.tiger.proxy.ui.pages.MainPage;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

@Slf4j
public class TigerProxySteps {

    private static final String XPATH_RES_BODY = "div[contains(@class, 'card full-width')";
    private static final String XPATH_REQUEST = "//div[@id='sidebar-menu']//div[contains(@class, 'menu-label')]";
    private static final String XPATH_RESPONSE = "//div[@id='sidebar-menu']//a[contains(@class, 'menu-label')]";
    private static final String CSS_ROUTESBLOCK = "#routeModalDialog > div.modal-content > article > div.message-body > div.routeListDiv.box";

    private static final String FILESUBSTRING = "tiger-report";
    final File downloadFolder;
    MainPage mainPage = new MainPage();
    Actions action = new Actions(mainPage.getDriver());

    public TigerProxySteps() {
        downloadFolder = Path.of(System.getProperty("user.home", "."), "Downloads").toFile();
        if (!downloadFolder.exists()) {
            if (!downloadFolder.mkdirs()) {
                throw new RuntimeException("Unable to create folder '" + downloadFolder.getAbsolutePath() + "'");
            }
        }
        RestAssured.proxy("127.0.0.1", UiTest.getProxyPort());
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

    public File assertReportDownloaded() {
        final AtomicReference<File> downloadedReport = new AtomicReference<>();
        try {
            await().atMost(10, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        assertThat(downloadFolder.listFiles())
                            .withFailMessage("Invalid download folder " + downloadFolder.getAbsolutePath()).isNotNull();
                        List<File> reportFiles = Arrays.stream(downloadFolder.listFiles())
                            .filter(file -> file.getName().contains(FILESUBSTRING))
                            .collect(Collectors.toList());
                        if (reportFiles.size() < 1) {
                            return false;
                        }
                        if (reportFiles.size() > 1) {
                            log.warn("Found more than 1 report (" + reportFiles.size() + ") taking first!");
                        }
                        downloadedReport.set(reportFiles.get(0));
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
        } catch (ConditionTimeoutException cte) {
            throw new AssertionError("Timed out after 10s trying to find downloaded report in folder " + downloadFolder.getAbsolutePath());
        }
        return downloadedReport.get();
    }

    public String openReport() throws IOException, InterruptedException {
        return IOUtils.toString(assertReportDownloaded().toURI(), StandardCharsets.UTF_8);
    }

    public void assertSeeMessageList() throws IOException, InterruptedException {
        assertThat(openReport())
            .contains("test1", "test2", "content-length: 36");
    }

    public void clickOnResetButton() {
        actionHelper("//button[@id='resetMsgs']");
    }

    public void assertReportNotSeen() {
        assertElemTextMatchesWithoutWhitespace("//div[@id='sidebar-menu']", "");
        assertElemTextMatchesWithoutWhitespace("//div[contains(@class, 'msglist')]", "");
    }

    public void clickOnQuit() {
        WebElement quitProxyButton = mainPage.getDriver().findElement(By.cssSelector("#quitProxy"));
        ((JavascriptExecutor) mainPage.getDriver()).executeScript("arguments[0].click();", quitProxyButton);

        WebDriverWait wait = new WebDriverWait(mainPage.getDriver(), 5);
        Function<WebDriver, Boolean> quitProxyDisabled = driver -> !driver.findElement(By.cssSelector("#quitProxy"))
            .isEnabled();
        wait.until(quitProxyDisabled);
    }

    public void assertRequestsFail(String testUrl1) {
        try {
            Response response = RestAssured.get(testUrl1).andReturn();
            assertThat(response).withFailMessage("Response is NULL!").isNotNull();
            assertThat(response.getStatusCode()).isNotEqualTo(200);
        } catch (Exception cex) {
            log.warn("CEX Class " + cex.getClass().getName());
            if (cex instanceof ConnectException) {
                log.info("Connection refused - As EXPECTED");
                return;
            }
            throw new AssertionError("Unexpected exception while checking for request failure " + cex, cex);

        }
    }

    public void assertRequestTimesOut(String testUrl1) {
        try {
            RestAssured.get(testUrl1).andReturn();
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
        assertElemTextMatchesWithoutWhitespace(
            "//div[@id='sidebar-menu']//div[contains(@class, 'menu-label')]/span[text()='" + idx + "']/..",
            idx + "REQUEST");
        assertElementExistsAndIsDisplayed(
            XPATH_REQUEST + "/span[text()='" + idx + "']/../following-sibling::div[starts-with(text(), 'GET\n" + path
                + "')]");
    }

    public void checkFalseRequest() {
        assertElemTextDoesntMatch("#sidebar-menu > div",
            String.format("%s\n%s\n%s%s", "5", "REQUEST", "GET ", "/test"));
    }

    public void checkResponse(String idx, String statusCode, String resBodyMessage) {
        assertElemTextMatchesWithoutWhitespace(XPATH_RESPONSE + "/span[text()='" + idx + "']/..", idx + "RESPONSE");

        WebElement resBody = mainPage.getDriver().findElement(By.xpath(
            "//" + XPATH_RES_BODY + "]//span[text()='" + idx + "']//ancestor::" + XPATH_RES_BODY + "]//h1[text()='" + statusCode
                + "']//ancestor::" + XPATH_RES_BODY + "]//pre[@class='json' and contains(text(), '" + resBodyMessage + "')]"));
        Assert.assertTrue("The response body with given parameters is displayed on the page", resBody.isDisplayed());
    }

    public void assertElemTextMatchesWithoutWhitespace(String xpath, String text) {
        WebElement el = mainPage.elemX(xpath);
        Assert.assertEquals(stripSpaceNewlines(text), stripSpaceNewlines(el.getText()));
    }

    public void assertElemTextDoesntMatch(String cssSelector, String text) {
        WebElement el = mainPage.getDriver().findElement(By.cssSelector(cssSelector));
        Assert.assertNotEquals(stripSpaceNewlines(text), stripSpaceNewlines(el.getText()));
    }

    private String stripSpaceNewlines(String str) {
        return str.replace("\n", "").replace(" ", "");
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
}

