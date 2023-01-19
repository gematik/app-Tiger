/*
 * Copyright (c) 2023 gematik GmbH
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
