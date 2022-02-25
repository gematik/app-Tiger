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
import net.serenitybdd.screenplay.actions.PerformActions;
import net.serenitybdd.screenplay.actions.ScrollTo;
import net.serenitybdd.screenplay.actions.ScrollToTarget;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.openqa.selenium.InvalidArgumentException;

public class PerformDragAction implements Task {

    private final Action action;
    private final Target source;
    private final Target destination;
    private final int yoffset;
    @SuppressWarnings("unused")
    private final String stepDescription;

    public PerformDragAction(Target source, Target destination, int yoffset, Action action) {
        this.source = source;
        this.destination = destination;
        this.yoffset = yoffset;
        this.action = action;
        stepDescription = action.descriptionOf(this);
    }

    // actions

    public static PerformDragAction dragsItemBelow(Target source, Target destination, int yoffset) {
        return instrumented(PerformDragAction.class, source, destination, yoffset, Action.dragsItemBelow);
    }

    public static PerformDragAction dragsItemAbove(Target source, Target destination, int yoffset) {
        return instrumented(PerformDragAction.class, source, destination, yoffset, Action.dragsItemAbove);
    }

    // implementation

    private void dragsItem(Actor actor) {
        actor.attemptsTo(
            PerformActions.with(
                actions -> actions.pause(500)
                    .clickAndHold(source.resolveFor(actor))
                    .pause(200)
                    .moveToElement(destination.resolveFor(actor), 0, yoffset)
                    .pause(200)
                    .release()
                    .perform()
            )
        );
    }

    @Override
    @Step("{0} #stepDescription")
    public <T extends Actor> void performAs(T actor) {
        action.execute(actor, this);
    }

    @RequiredArgsConstructor
    public enum Action {
        dragsItemAbove((actor, instance) -> instance.dragsItem(actor)),
        dragsItemBelow((actor, instance) -> instance.dragsItem(actor));

        private final BiConsumer<Actor, PerformDragAction> actionConsumer;

        void execute(Actor actor, PerformDragAction instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(PerformDragAction instance) {
            switch (instance.action) {
                case dragsItemAbove:
                    return " drags item " + instance.source + " above item " + instance.destination;
                case dragsItemBelow:
                    return " drags item " + instance.source + " below item " + instance.destination;
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }
    }
}
