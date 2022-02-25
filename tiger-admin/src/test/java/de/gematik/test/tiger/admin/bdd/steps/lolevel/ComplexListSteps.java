/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
