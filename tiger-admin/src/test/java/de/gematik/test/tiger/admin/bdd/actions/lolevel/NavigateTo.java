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

package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import de.gematik.test.tiger.admin.bdd.SpringBootDriver;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Open;
import net.serenitybdd.screenplay.ensure.Ensure;

@Slf4j
public class NavigateTo {

    public static Performable adminUIHomePage() {
        return Task.where("{0} opens the Admin UI home page",
            Open.url("http://127.0.0.1:" + SpringBootDriver.getAdminPort()),
            Ensure.that(AdminHomePage.WELCOME_CARD).isDisplayed(),
            Ensure.that(AdminHomePage.testenvMenuItem("btn-new-testenv")).attribute("disabled").isEqualTo("true"),
            Ensure.that(
                PerformActionsOnSnack.snackWithTextStartingWith("Templates loaded")
                    .waitingForNoMoreThan(Duration.ofSeconds(5))).isDisplayed(),
            Ensure.that(
                PerformActionsOnSnack.snackWithTextStartingWith("ConfigScheme loaded")
                    .waitingForNoMoreThan(Duration.ofSeconds(5))).isDisplayed(),
            Pause.pauseFor(500) // to allow initialization ajax calls to finish
        );
    }
}
