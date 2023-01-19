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
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import net.serenitybdd.core.pages.WebElementFacade;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;

public class InputFieldSteps {

    @And("she/he sees {word} field {string}")
    public void seesField(final String type, String name) {
        Target fieldTarget;
        if (type.equals("multiselect")) {
            fieldTarget = ServerFormular.getMultiSelectField(theActorInTheSpotlight(), name);
        } else {
             fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), name);
        }
        WebElementFacade el = fieldTarget.resolveFor(theActorInTheSpotlight());
        List<Performable> tasks = new ArrayList<>();
        tasks.add(Ensure.that(fieldTarget).isDisplayed());
        switch (type) {
            case "input":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("input"));
                break;
            case "text":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("textarea"));
                break;
            case "check":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("input"));
                tasks.add(Ensure.that(el.getAttribute("type")).isEqualTo("checkbox"));
                break;
            case "select":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("select"));
                break;
            case "multiselect":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("div"));
                tasks.add(Ensure.that(el.getElement().getAttribute("class")).contains("dashboardcode-bsmultiselect"));
                break;
            case "list":
                tasks.add(Ensure.that(el.getTagName()).isEqualTo("ul"));
                break;
        }
        theActorInTheSpotlight().attemptsTo(tasks.toArray(new Performable[0]));
    }

    @And("she/he doesn't see field {string}")
    public void doesntSeeField(String name) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), name);
        theActorInTheSpotlight().attemptsTo(Ensure.that(fieldTarget).isNotDisplayed());
    }

    @And("she/he sees section {string}")
    public void seesSection(String section) {
        Target fieldTarget = ServerFormular.getSection(section);
        theActorInTheSpotlight().attemptsTo(Ensure.that(fieldTarget).isDisplayed());
    }
    @But("she/he doesn't see section {string}")
    public void doesntSeeSection(String section) {
        Target fieldTarget = ServerFormular.getSection(section);
        theActorInTheSpotlight().attemptsTo(Ensure.that(fieldTarget).isNotDisplayed());
    }

    @And("she/he enters {string} into field {string}")
    public void entersValueIntoField(String value, String name) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), name);
        theActorInTheSpotlight().attemptsTo(
            Enter.theValue(value).into(fieldTarget)
        );
    }
    @And("she/he checks field {string} contains {string}")
    public void checksFieldContainsValue(String name, String value) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), name);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget).hasValue(value)
        );
    }
}
