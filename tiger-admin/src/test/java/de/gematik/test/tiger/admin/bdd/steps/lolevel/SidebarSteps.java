package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformDragAction;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.ensure.Ensure;

public class SidebarSteps {

    @And("she/he drags sidebar item {string} below {string}")
    public void dragsSidebarItemBelow(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemBelow(AdminHomePage.dragHandleOfSidebarItem(srcNodeName), AdminHomePage.sidebarItemOf(destNodeName), 10)
        );
    }

    @And("she/he drags sidebar item {string} above {string}")
    public void dragsSidebarItemAbove(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemAbove(AdminHomePage.dragHandleOfSidebarItem(srcNodeName), AdminHomePage.sidebarItemOf(destNodeName), -10)
        );
    }

    @Then("nodes are ordered {string}")
    public void nodesAreOrdered(String nodeNamesCSV) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(String.join(",", AdminHomePage.getSidebarItemNameList().answeredBy(theActorInTheSpotlight())))
                .isEqualTo(nodeNamesCSV)
        );
    }
    @Then("formulars are ordered {string}")
    public void formularsAreOrdered(String nodeNamesCSV) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(String.join(",", AdminHomePage.getFormularNameList().answeredBy(theActorInTheSpotlight())))
                .isEqualTo(nodeNamesCSV)
        );
    }

    @When("she/he tries to drag sidebar item {string} above {string}")
    public void triesToDragSidebarItemAbove(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemAbove(AdminHomePage.sidebarItemOf(srcNodeName), AdminHomePage.sidebarItemOf(destNodeName), -10)
        );
    }
    @When("she/he tries to drag sidebar item {string} below {string}")
    public void triesToDragSidebarItemBelow(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemBelow(AdminHomePage.sidebarItemOf(srcNodeName), AdminHomePage.sidebarItemOf(destNodeName), 10)
        );
    }
}
