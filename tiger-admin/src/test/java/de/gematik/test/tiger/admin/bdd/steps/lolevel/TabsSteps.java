package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.ShowTabInFormular;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import net.serenitybdd.screenplay.ensure.Ensure;

public class TabsSteps {

    @And("she/he sees tab link {string}")
    public void heSeesTabLink(String tabLink) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(ShowTabInFormular.tabLink(tabLink)).isDisplayed()
        );
    }

    @But("she/he doesn't see tab link {string}")
    public void heDoesntSeeTabLink(String tabLink) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(ShowTabInFormular.tabLink(tabLink)).isNotDisplayed()
        );
    }
}
