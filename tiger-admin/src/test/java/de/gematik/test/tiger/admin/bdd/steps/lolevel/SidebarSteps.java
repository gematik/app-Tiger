/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformDragAction;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.ScrollToTarget;
import net.serenitybdd.screenplay.ensure.Ensure;

public class SidebarSteps {

    @And("she/he drags sidebar item {string} below {string}")
    public void dragsSidebarItemBelow(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            new ScrollToTarget(AdminHomePage.sidebarHeader()).andAlignToTop(),
            PerformDragAction.dragsItemBelow(
                AdminHomePage.dragHandleOfSidebarItem(srcNodeName),
                AdminHomePage.sidebarItemOf(destNodeName),
                10)
        );
    }

    @And("she/he drags sidebar item {string} above {string}")
    public void dragsSidebarItemAbove(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            new ScrollToTarget(AdminHomePage.sidebarHeader()).andAlignToTop(),
            PerformDragAction.dragsItemAbove(
                AdminHomePage.dragHandleOfSidebarItem(srcNodeName),
                AdminHomePage.sidebarItemOf(destNodeName),
                -10)
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
            new ScrollToTarget(AdminHomePage.sidebarHeader()).andAlignToTop(),
            PerformDragAction.dragsItemAbove(
                AdminHomePage.sidebarItemOf(srcNodeName),
                AdminHomePage.sidebarItemOf(destNodeName),
                -10)
        );
    }

    @When("she/he tries to drag sidebar item {string} below {string}")
    public void triesToDragSidebarItemBelow(String srcNodeName, String destNodeName) {
        theActorInTheSpotlight().attemptsTo(
            new ScrollToTarget(AdminHomePage.sidebarHeader()).andAlignToTop(),
            PerformDragAction.dragsItemBelow(
                AdminHomePage.sidebarItemOf(srcNodeName),
                AdminHomePage.sidebarItemOf(destNodeName),
                10)
        );
    }

    @And("he duplicates node {string}")
    public void heDuplicatesNode(String nodeName) {
        theActorInTheSpotlight().attemptsTo(
            new ScrollToTarget(AdminHomePage.sidebarHeader()).andAlignToTop(),
            new ScrollToTarget(ServerFormular.sidebarItemContextMenu(nodeName)).andAlignToBottom(),
            Click.on(ServerFormular.sidebarItemContextMenu(nodeName)),
            Click.on(ServerFormular.sidebarItemContextMenuEntry("duplicate"))
        );
    }
}
