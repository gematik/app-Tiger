/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.And;
import net.serenitybdd.screenplay.actions.Click;

public class CollapseSteps {

    @And("he collapses the node")
    public void heCollapsesNode() {
        theActorInTheSpotlight().attemptsTo(
            Click.on(ServerFormular.nodeCollapseIcon(theActorInTheSpotlight()))
        );
    }
}
