/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.admin.bdd.actions.lolevel;

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

public class PerformActionsOnSnack implements Task {

    public static Target SNACK_OPEN = Target.the("the open snack")
        .locatedBy("//div[contains(@class, 'toast')]");
    public static Target SNACK_CLOSE_ICON = Target.the("close icon in snack")
        .locatedBy("div.toast button.btn-close");
    private final Action action;

    @SuppressWarnings("FieldCanBeLocal")
    private final String stepDescription;

    public PerformActionsOnSnack(Action action) {
        this.action = action;
        this.stepDescription = action.descriptionOf(this);
    }

    public static PerformActionsOnSnack closeSnack() {
        return instrumented(PerformActionsOnSnack.class, Action.closeSnack);
    }

    public static Target snackWithTextStartingWith(String startsWithText) {
        return Target.the("feedback snack starting with '" +  startsWithText + "'")
            .locatedBy("//div[contains(@class, 'toast') and starts-with(text(), '" + startsWithText + "')]");
    }

    public static Target snackWithTextContaining(String containsText) {
        return Target.the("feedback snack containing '" +  containsText + "'")
            .locatedBy("//div[contains(@class, 'toast') and contains(text(), '" + containsText + "')]");
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

        private final BiConsumer<Actor, PerformActionsOnSnack> actionConsumer;

        void execute(Actor actor, PerformActionsOnSnack instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(PerformActionsOnSnack instance) {
            switch (instance.action) {
                case closeSnack:
                    return " closes open snack";
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }
    }
}
