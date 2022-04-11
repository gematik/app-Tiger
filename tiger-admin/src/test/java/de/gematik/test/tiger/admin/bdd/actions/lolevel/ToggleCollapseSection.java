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
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import java.time.Duration;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Scroll;
import net.serenitybdd.screenplay.actions.ScrollToBy;
import net.serenitybdd.screenplay.actions.ScrollToTarget;
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
            new ScrollToTarget(legend).andAlignToBottom(),
            Pause.pauseFor(500), // needed as scroll seems to be async and causes issues of not clickable elem
            Ensure.that(legend.waitingForNoMoreThan(Duration.ofMillis(500L))).isDisplayed(),
            Click.on(legend),
            new ScrollToTarget(legend).andAlignToTop()
            // TODO check that it toggled by first getting classes and check it toggled,
        );
    }
}
