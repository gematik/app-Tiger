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

public class ShowTabInFormular implements Task {

    private TestContext ctxt = new TestContext("tiger.admin.usecases");
    private String tabName;

    public ShowTabInFormular(String tabName) {
        this.tabName = tabName;
    }

    public static ShowTabInFormular withName(String tabName) {
        return instrumented(ShowTabInFormular.class, tabName);
    }

    @Override
    @Step("{0} shows tab #tabName in formular #serverKey,")
    public <T extends Actor> void performAs(T actor) {
        ServerFormular form = new ServerFormular(ctxt.getString("serverKey"));
        Target tabLink = form.getTabLink(tabName);
        actor.attemptsTo(
            // Actions
            Ensure.that(tabLink).isEnabled(),
            Click.on(tabLink),
            Ensure.that(tabLink).hasCssClass("active")
        );
    }
}
