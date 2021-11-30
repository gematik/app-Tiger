package de.gematik.test.tiger.admin.bdd.actions;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.openqa.selenium.InvalidArgumentException;

public class SnackActions implements Task {

    public static Target SNACK_OPEN = Target.the("the open snack")
        .locatedBy("//div[contains(@class, 'toast')]");
    public static Target SNACK_CLOSE_ICON = Target.the("close icon in snack")
        .locatedBy("div.toast button.btn-close");
    private final Action action;

    @SuppressWarnings("FieldCanBeLocal")
    private final String stepDescription;

    public SnackActions(Action action) {
        this.action = action;
        this.stepDescription = action.descriptionOf(this);
    }

    public static SnackActions closeSnack() {
        return instrumented(SnackActions.class, Action.closeSnack);
    }

    public static Target getSnackWithTextStartingWith(String startsWithText) {
        return Target.the("feedback snack for server added")
            .locatedBy("//div[contains(@class, 'toast') and starts-with(text(), '" + startsWithText + "')]");
    }

    // implementation

    public <T extends Actor> void closeSnack(T actor) {
        actor.attemptsTo(
            Ensure.that(SNACK_OPEN).isDisplayed(),
            Click.on(SNACK_CLOSE_ICON)
        );
    }

    @Override
    @Step("{0} #stepDescription")
    public <T extends Actor> void performAs(T actor) {
        action.execute(actor, this);
    }

    @RequiredArgsConstructor
    public enum Action {
        closeSnack((actor, instance) -> instance.closeSnack(actor));

        private final BiConsumer<Actor, SnackActions> actionConsumer;

        void execute(Actor actor, SnackActions instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(SnackActions instance) {
            switch (instance.action) {
                case closeSnack:
                    return " closes open snack";
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }
    }
}
