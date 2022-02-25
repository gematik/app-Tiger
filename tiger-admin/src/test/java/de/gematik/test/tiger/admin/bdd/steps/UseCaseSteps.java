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

package de.gematik.test.tiger.admin.bdd.steps;

import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.AddServer;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.*;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.actions.SendKeys;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.actors.OnlineCast;
import net.serenitybdd.screenplay.annotations.CastMember;
import net.serenitybdd.screenplay.ensure.Ensure;
import org.openqa.selenium.Keys;

public class UseCaseSteps {

    @CastMember
    Actor Gerriet;

    @Before
    public void setTheStage() {
        OnStage.setTheStage(new OnlineCast());
    }

    @Given("{word} is on the homepage")
    public void isOnTheHomepage(String actor) {
        theActorCalled(actor).attemptsTo(NavigateTo.adminUIHomePage());
    }

    @And("she/he adds a {string} node via welcome screen")
    public void addsANodeViaWelcomeScreen(String nodeType) {
        theActorInTheSpotlight().attemptsTo(
            AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_WELCOME_CARD, false));
    }

    @And("she/he adds a {string} node via welcome screen with Enter")
    public void addsANodeViaWelcomeScreenWithEnter(String nodeType) {
        theActorInTheSpotlight().attemptsTo(
            AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_WELCOME_CARD, true));
    }

    @And("she/he adds a {string} node via sidebar")
    public void addsANodeViaSidebar(String nodeType) {
        theActorInTheSpotlight().attemptsTo(AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_SIDEBAR, false));
    }

    @And("she/he adds a {string} node via sidebar with Enter")
    public void addsANodeViaSidebarWithEnter(String nodeType) {
        theActorInTheSpotlight().attemptsTo(AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_SIDEBAR, true));
    }

    @And("she/he focuses on formular {string}")
    public void focusesOnFormular(String serverKey) {
        theActorInTheSpotlight().remember("serverKey", serverKey);
        theActorInTheSpotlight().attemptsTo(Click.on(ServerFormular.sidebarItem(theActorInTheSpotlight())));
    }

    @And("she/he shows {string} tab")
    public void showsTabInFormular(String tabName) {
        theActorInTheSpotlight().attemptsTo(ShowTabInFormular.withName(tabName));
    }

    @And("she/he folds/unfolds section {string}")
    public void unfoldsSection(String section) {
        theActorInTheSpotlight().attemptsTo(ToggleCollapseSection.withName(section));
    }

    @When("she/he deletes node {string}")
    public void deletesNode(String nodeName) {
        theActorInTheSpotlight().attemptsTo(
            Click.on(ServerFormular.sidebarItemContextMenu(nodeName)),
            Click.on(ServerFormular.sidebarItemContextMenuEntry("delete"))
        );
    }

    @And("she/he renames the node to {string}")
    public void renamesTheNodeTo(String newNodeName) {
        theActorInTheSpotlight().attemptsTo(
            Click.on(ServerFormular.getHeading()),
            Enter.theValue(newNodeName).into(ServerFormular.getHeading()).thenHit(Keys.ENTER)
        );
    }

    @And("she/he enters {string} as new name for the node")
    public void entersNewNameForNode(String newNodeName) {
        theActorInTheSpotlight().attemptsTo(
            Click.on(ServerFormular.getHeading()),
            Enter.theValue(newNodeName).into(ServerFormular.getHeading())
        );
    }

    @And("she/he aborts node renaming pressing ESC")
    public void abortRenamingNodePressingEsc() {
        theActorInTheSpotlight().attemptsTo(
            SendKeys.of(Keys.ESCAPE).into(ServerFormular.getHeading())
        );
    }

    @When("she/he creates a new test environment")
    public void createsANewTestEnvironment() {
        theActorInTheSpotlight().attemptsTo(
            Click.on(AdminHomePage.testenvMenu()),
            Click.on(AdminHomePage.testenvMenuItem("btn-new-testenv"))
        );
    }

    @When("she/he confirms modal")
    public void confirmsModal() {
        theActorInTheSpotlight().attemptsTo(
            Click.on(AdminHomePage.yesButtonOnConfirmModal())
        );
    }

    @When("she/he dismisses confirm modal")
    public void dismissesConfirmModal() {
        theActorInTheSpotlight().attemptsTo(
            Click.on(AdminHomePage.noButtonOnConfirmModal())
        );
    }


    @Then("she/he sees welcome screen")
    public void seesWelcomeScreen() {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(AdminHomePage.WELCOME_CARD).isDisplayed(),
            Ensure.that(
                    PerformActionsOnSnack.snackWithTextContaining(
                            "Loaded yaml file")
                        .waitingForNoMoreThan(Duration.ofSeconds(3)))
                .isNotDisplayed(),
            Ensure.that(AdminHomePage.testenvMenuItem("btn-new-testenv")).attribute("disabled").isEqualTo("true")
        );
    }

    @And("she/he doesn't see sidebar header")
    public void doesntSeeSidebarHeader() {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(AdminHomePage.sidebarHeader()).isNotDisplayed()
        );
    }

    @Then("she/he saves test environment as {string} using Enter")
    public void savesTestEnvironmentUsingEnter(String newSaveName) {
        theActorInTheSpotlight().attemptsTo(SaveAsAction.ofTypeVia(newSaveName, true));
    }

    @Then("she/he verifies saved file {string} contains")
    public void verifySavedContentMatches(String fileName, String docString) {
        theActorInTheSpotlight().attemptsTo(VerifyFileContentAction.ofTypeVia(fileName, docString));
    }
}
