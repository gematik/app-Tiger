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
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformActionsOnList;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformDragAction;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.actions.Scroll;
import net.serenitybdd.screenplay.ensure.Ensure;

public class SimpleListSteps {

    @And("she/he tests list {string}")
    public void testsList(String listName) {
        theActorInTheSpotlight().remember("listName", listName);
    }

    @When("she/he adds list item {string}")
    public void addsEntry(String itemText) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.addsItem(itemText, true));
    }

    @Then("she/he checks list item in row {int} has value {string}")
    public void checksEntryWithIndexHasValue(int row, String value) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnList.getValueOfItem(row)).isEqualTo(value)
        );
    }

    @When("she/he selects list item in row {int}")
    public void selectsEntryWithIndex(int row) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.selectsItem(row));
    }

    @When("she/he sets active list item to {string}")
    public void setsActiveEntryTo(String newValue) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.setsValueForActiveItemTo(newValue, true));
    }

    @When("she/he enters {string} to active list item")
    public void entersToActiveItem(String newValue) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.setsValueForActiveItemTo(newValue, false));
    }

    @And("she/he presses ESC on editing list item")
    public void pressesESC() {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.pressesEsc());
    }

    @And("she/he deletes active list item")
    public void deletesActiveItem() {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.deletesActiveItem());
    }

    @Given("she/he checks list length is {int}")
    public void checksListLengthIs(int length) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnList.getListSize()).isEqualTo(length)
        );
    }

    @And("she/he checks that add button for simple list is enabled")
    public void checksAddButtonIsEnabled() {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnList.simpleListAddButton()).isEnabled()
        );
    }

    @And("she/he checks that add button for complex list is enabled")
    public void checksComplexListAddButtonIsEnabled() {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnList.complexListAddButton()).isEnabled()
        );
    }
    @And("she/he checks active list item is in row {int}")
    public void checksActiveEntryHasIndex(int activeRow) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnList.askForActiveItemIndex()).isEqualTo(activeRow)
        );
    }

    @When("she/he drags list item in row {int} below item in row {int}")
    public void dragsItemBelowItem(int srcRow, int destRow) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemBelow(PerformActionsOnList.dragHandleOfItemInRow(srcRow),
                PerformActionsOnList.listItemInRow(destRow),
                10)
        );
    }

    @When("she/he drags list item in row {int} above item in row {int}")
    public void dragsItemAboveItem(int srcRow, int destRow) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemAbove(PerformActionsOnList.dragHandleOfItemInRow(srcRow),
                PerformActionsOnList.listItemInRow(destRow),
                -10)
        );
    }

    @Given("she/he scrolls to row {int} in list")
    public void scrollsToItem(int row) {
        theActorInTheSpotlight().attemptsTo(
            Scroll.to(PerformActionsOnList.listItemInRow(row))
        );
    }

    @When("she/he tries to drag list item in row {int} above item in row {int}")
    public void heTriesToDragListItemInRowAboveItemInRow(int srcRow, int destRow) {
        theActorInTheSpotlight().attemptsTo(
            PerformDragAction.dragsItemAbove(PerformActionsOnList.listItemInRow(srcRow),
                PerformActionsOnList.listItemInRow(destRow),
                -10)
        );
    }
}
