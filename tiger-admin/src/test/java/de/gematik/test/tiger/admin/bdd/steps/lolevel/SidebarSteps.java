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

package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.Pause;
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
            Pause.pauseFor(500),
            Click.on(ServerFormular.sidebarItemContextMenu(nodeName)),
            Click.on(ServerFormular.sidebarItemContextMenuEntry("duplicate"))
        );
    }
}
