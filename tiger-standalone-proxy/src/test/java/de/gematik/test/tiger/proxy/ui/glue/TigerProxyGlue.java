package de.gematik.test.tiger.proxy.ui.glue;

import de.gematik.test.tiger.proxy.TigerStandaloneProxyApplication;
import de.gematik.test.tiger.proxy.ui.MockServerPlugin;
import de.gematik.test.tiger.proxy.ui.steps.TigerProxySteps;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import net.thucydides.core.annotations.Steps;
import org.awaitility.core.ConditionTimeoutException;
import org.openqa.selenium.NoAlertPresentException;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Slf4j
public class TigerProxyGlue {

    @Steps
    TigerProxySteps steps = new TigerProxySteps();

    public TigerProxyGlue() {
    }

    @Given("I open the main page")
    public void iOpenPage() {
        steps.openPage();
    }

    @Then("I am on the main page")
    public void iAmOnPage() {
        steps.assertOnPage();
    }

    @When("I click on the Routes button")
    public void iClickOnRoutesButton() {
        steps.clickOnRoutesButton();
    }

    @When("I remove the existing route there")
    public void iRemoveRoute() {
        steps.removeExistingRoute();
    }

    @Then("The route from {string} to {string} is deleted")
    public void assertRouteDeleted(String deletedFrom, String deletedTo) {
        steps.assertRouteDeleted(deletedFrom, deletedTo);
    }

    @When("I add a new route from {string} to {string}")
    public void iAddNewRoute(String from, String to) {
        steps.addNewRoute(patchPath(from), patchPath(to));
    }

    private String patchPath(String uri) {
        return uri
            .replace("${serverport}", MockServerPlugin.getMockServerPort());
    }

    @And("I close the route dialog")
    public void iCloseTheRouteDialog() {
        steps.closeRouteDlg();
    }

    @Then("I see the new route from {string} to {string}")
    public void assertSeeNewRoute(String from, String to) {
        steps.assertSeeNewRoute(patchPath(from), patchPath(to));
    }

    @When("I send successful request to {string}")
    public void assertSeeNewRoute(String to) {
        steps.sendRequest(to, 200);
    }

    @Then("I see the updated message list")
    public void assertSeeReport() {
        steps.assertReportDisplayed();
    }

    @When("I click on the Save button")
    public void iClickOnSaveButton() {
        steps.clickOnSaveButton();
    }

    @Then("The entries in message list are downloaded")
    public void assertReportDownloaded() throws InterruptedException {
        steps.assertReportDownloaded();
    }

    @When("I open the report")
    public void iOpenTheReport() throws IOException, InterruptedException {
        steps.openReport();
    }

    @Then("I see the contents of the updated message list")
    public void assertSeeMessageList() throws IOException, InterruptedException {
        steps.assertSeeMessageList();
    }

    @Then("I don't see a message entry for this route")
    public void assertSeeEmptyMessageList() {
        steps.assertReportDisplayed();
    }

    @When("I click on the Reset button")
    public void iClickOnResetButton() {
        steps.clickOnResetButton();
    }

    @Then("Report is empty")
    public void assertReportNotSeen() {
        steps.assertReportNotSeen();
    }


    @When("I click on the Quit button")
    public void iClickOnQuit() {
        steps.clickOnQuit();
    }

    @Then("The request to {string} fail")
    public void assertRequestsFail(String testUrl) throws NoAlertPresentException {
        steps.assertRequestsFail(testUrl);
    }

    @Given("I started the standalone proxy")
    public void iStartedTheStandaloneProxy() {
        SpringApplication.run(TigerStandaloneProxyApplication.class, "--spring.profiles.active=uitests");
    }

    @And("I switch to manual update mode")
    public void iSwitchToManualUpdateMode() {
        steps.switchToUpdateMode("noupdate");
    }

    @And("I update message list")
    public void iUpdateMessageList() {
        steps.clickUpdate();
    }

    @And("I purge the download folder of previous reports")
    public void iPurgeTheDownloadFolderOfPreviousReports() {
        steps.purgeDownloadFolder();
    }

    @And("I switch to test quit mode on UI")
    public void iSwitchToTestQuitModeOnUI() {
        steps.switchToTestQuitMode();
    }

    @And("I wait {int} seconds")
    public void iWaitSeconds(int waitsec) {
        log.info("waiting for " + waitsec + " seconds...");
        try {
            await().atMost(waitsec, TimeUnit.SECONDS).until(() -> false);
        } catch (ConditionTimeoutException ignored) {

        }
    }

    @When("I quit the tiger proxy via UI")
    public void iQuitTheTigerProxyViaUI() {
        steps.clickOnQuit();
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            try {
                steps.assertRequestTimesOut("http://127.0.0.1:8080/webui");
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Then("The request to {string} times out")
    public void theRequestToTimesOut(String url) {
        steps.assertRequestTimesOut(url);
    }
}
