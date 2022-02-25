/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformActionsOnSnack;
import io.cucumber.java.en.And;
import net.serenitybdd.screenplay.ensure.Ensure;

public class SnackSteps {

    @And("he sees snack starting with {string}")
    public void heSeesSnackStartingWith(String startsWithText) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnSnack.snackWithTextStartingWith(startsWithText)).isDisplayed()
        );
    }

    @And("she/he closes open snack")
    public void closesOpenSnack() {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnSnack.closeSnack());
    }
}
