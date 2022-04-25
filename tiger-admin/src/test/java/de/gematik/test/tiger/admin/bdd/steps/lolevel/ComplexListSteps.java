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
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.ensure.Ensure;

public class ComplexListSteps {

    @When("he adds complex list item")
    public void addsComplexListItem(String docstring) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.addsComplexItem(docstring, true));
    }

    @When("he opens complex list edit fields by clicking on add entry button")
    public void opensComplexListEditFieldsByClickingOnAddEntryButton() {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.opensComplexFieldset());
    }

    @When("he sets active complex list item to")
    public void setsActiveComplexListItemTo(String docstring) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.setsValueForActiveComplexItemTo(docstring, true));
    }

    @When("he enters values to the active complex list item")
    public void heEntersValuesToTheActiveComplexListItem(String docstring) {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnList.setsValueForActiveComplexItemTo(docstring, false));
    }

    @And("complex list item field {string} has value {string}")
    public void complexListItemFieldHasValue(String fieldName, String value) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(
                ServerFormular.getInputField(theActorInTheSpotlight(), theActorInTheSpotlight().recall("listName") +"." + fieldName)
            ).value().isEqualTo(value)
        );

    }
}
