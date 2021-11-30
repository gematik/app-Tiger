package de.gematik.test.tiger.admin.bdd.actions;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import de.gematik.test.tiger.common.context.TestContext;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;

public class ToggleCollapseSection implements Task {

    private String section;

    private TestContext ctxt = new TestContext("tiger.admin.usecases");

    public ToggleCollapseSection(String section) {
        this.section = section;
    }

    public static ToggleCollapseSection withName(String section) {
        return instrumented(ToggleCollapseSection.class, section);
    }

    @Override
    @Step("{0} toggles section #section")
    public <T extends Actor> void performAs(T actor) {
        ServerFormular form = new ServerFormular(ctxt.getString("serverKey"));
        Target legend = form.getSectionLegend(section);
        actor.attemptsTo(
            // Actions
            Ensure.that(legend).isEnabled(),
            Click.on(legend)
            // TODO check that it toggled by first getting classes and check it toggled,
        );
    }
}
