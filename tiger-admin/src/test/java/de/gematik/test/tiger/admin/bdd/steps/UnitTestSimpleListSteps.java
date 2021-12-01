package de.gematik.test.tiger.admin.bdd.steps;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.SimpleListActions;
import de.gematik.test.tiger.common.context.TestContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.actors.OnlineCast;

public class UnitTestSimpleListSteps {

    TestContext ctxt = new TestContext("tiger.admin.simpleList");

    @Before
    public void setTheStage() {
        OnStage.setTheStage(new OnlineCast());
    }


    @And("she/he tests simple list {string} in formular {string}")
    public void gerrietTestsSimpleListInFormular(String listName, String serverKey) {
        ctxt.getContext().put("listName", listName);
        ctxt.getContext().put("serverKey", serverKey);
    }

    @When("she/he adds entry {string}")
    public void gerrietAddsEntry(String itemText) {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.addEntry(itemText, true));
    }

    @Then("she/he checks entry with index {int} has value {string}")
    public void gerrietChecksEntryWithIndexHasValue(int itemIndex, String value) {
        theActorInTheSpotlight().asksFor(SimpleListActions.getValueOfItem(itemIndex)).equals(value);
    }

    @When("she/he selects entry with index {int}")
    public void gerrietSelectsEntryWithIndex(int index) {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.selectEntry(index));
    }

    @When("she/he sets active entry to {string}")
    public void gerrietSetsActiveEntryTo(String newValue) {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.setValueForActiveItemTo(newValue, true));
    }

    @When("she/he enters {string} to active item")
    public void gerrietEntersToActiveItem(String newValue) {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.setValueForActiveItemTo(newValue, false));
    }

    @And("she/he presses ESC")
    public void gerrietPressesESC() {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.pressEsc());
    }

    @And("he deletes active item")
    public void heDeletesActiveItem() {
        theActorInTheSpotlight().attemptsTo(SimpleListActions.deletesActiveItem());
    }
}
