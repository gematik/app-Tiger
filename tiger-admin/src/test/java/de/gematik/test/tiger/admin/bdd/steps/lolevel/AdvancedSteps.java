package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.actions.lolevel.Pause;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Scroll;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;

public class AdvancedSteps {

    @When("he clicks on global advanced icon")
    public void clicksOnGlobalAdvancedIcon() {
        Target icon = ServerFormular.globalAdvancedIcon(theActorInTheSpotlight());
        boolean active = icon.resolveFor(theActorInTheSpotlight()).hasClass("active");
        theActorInTheSpotlight().attemptsTo(
            Scroll.to(icon).andAlignToBottom(),
            Pause.pauseFor(500),
            Click.on(icon),
            active?
                Ensure.that(icon).not().hasCssClass("active") :
                Ensure.that(icon).hasCssClass("active"),
            // fading out takes its time
            Pause.pauseFor(1000)
            );
    }

    @When("he clicks on advanced icon in section {string}")
    public void clicksOnAdvancedIconInSection(String section) {
        theActorInTheSpotlight().attemptsTo(
            Click.on(ServerFormular.advancedIconOfSection(theActorInTheSpotlight(), section)),
            // fading out takes its time
            Pause.pauseFor(1000)
            );
    }
}
