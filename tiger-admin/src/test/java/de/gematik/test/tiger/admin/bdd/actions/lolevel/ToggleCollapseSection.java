package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Scroll;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;

public class ToggleCollapseSection implements Task {

    private final String section;

    public ToggleCollapseSection(String section) {
        this.section = section;
    }

    public static ToggleCollapseSection withName(String section) {
        return instrumented(ToggleCollapseSection.class, section);
    }

    public Target sectionLegend(String section) {
        return Target.the("legend for section " + section)
            .locatedBy(ServerFormular.xPath() + "//fieldset[@section='" + section + "']/legend");
    }

    @Override
    @Step("{0} toggles section #section")
    public <T extends Actor> void performAs(T actor) {
        ServerFormular form = new ServerFormular(actor.recall("serverKey"));
        Target legend = sectionLegend(section);
        actor.attemptsTo(
            // Actions
            Ensure.that(legend).isEnabled(),
            Scroll.to(legend).andAlignToBottom(),
            Click.on(legend)
            // TODO check that it toggled by first getting classes and check it toggled,
        );
    }
}
