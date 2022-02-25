/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Scroll;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;

public class ShowTabInFormular implements Task {

    private final String tabName;

    public ShowTabInFormular(String tabName) {
        this.tabName = tabName;
    }

    public static ShowTabInFormular withName(String tabName) {
        return instrumented(ShowTabInFormular.class, tabName);
    }

    public static Target tabLink(String tabName) {
        return Target.the("link for tab " + tabName)
            .locatedBy("//form[@id='content_server_" + theActorInTheSpotlight().recall("serverKey") + "']"
                + "//a[contains(@class,'nav-link') and text()='" + tabName + "']");
    }

    @Override
    @Step("{0} shows tab #tabName")
    public <T extends Actor> void performAs(T actor) {
        Target tabLink = tabLink(tabName);
        actor.attemptsTo(
            // as top navbar hides the entry we need to be creative here
            Scroll.to(tabLink).andAlignToBottom(),
            // Actions
            Click.on(tabLink),
            Ensure.that(tabLink).hasCssClass("active")
        );
    }
}
