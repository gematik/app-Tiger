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
import de.gematik.test.tiger.admin.bdd.actions.lolevel.PerformActionsOnSnack;
import io.cucumber.java.en.And;
import net.serenitybdd.screenplay.ensure.Ensure;

public class SnackSteps {

    @And("he sees snack starting with {string}")
    public void heSeesSnackStartingWith(String startsWithText) {
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(PerformActionsOnSnack.snackWithTextStartingWith(startsWithText)).isDisplayed()
        );
    }

    @And("she/he closes open snack")
    public void closesOpenSnack() {
        theActorInTheSpotlight().attemptsTo(PerformActionsOnSnack.closeSnack());
    }
}
