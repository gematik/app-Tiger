package de.gematik.test.tiger.admin.bdd.steps;

import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.*;
import de.gematik.test.tiger.common.context.TestContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.actors.OnlineCast;
import net.serenitybdd.screenplay.annotations.CastMember;

public class UseCaseSteps {

    TestContext ctxt = new TestContext("tiger.admin.usecases");

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
        theActorInTheSpotlight().attemptsTo(AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_WELCOME_CARD));
    }

    @And("she/he adds a {string} node via sidebar")
    public void addsANodeViaSidebar(String nodeType) {
        theActorInTheSpotlight().attemptsTo(AddServer.ofTypeVia(nodeType, AddServer.BTN_ADD_SERVER_ON_SIDEBAR));
    }

    @And("she/he focuses on formular {string}")
    public void heFocusesOnFormular(String serverKey) {
        ctxt.putString("serverKey", serverKey);
    }

    @And("she/he shows {string} tab")
    public void heShowsTabInFormular(String tabName) {
        theActorInTheSpotlight().attemptsTo(ShowTabInFormular.withName(tabName));
    }

    @And("she/he unfolds section {string}")
    public void heUnfoldsSection(String section) {
        theActorInTheSpotlight().attemptsTo(ToggleCollapseSection.withName(section));
    }

    @And("she/he closes open snack")
    public void gerrietClosesOpenSnack() {
        theActorInTheSpotlight().attemptsTo(SnackActions.closeSnack());
    }
}
