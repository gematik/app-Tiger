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

package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.Then;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.screenplay.ensure.Ensure;

@Slf4j
public class SummarySteps {

    @Then("the summary of section {string} matches")
    public void theSummaryMatches(String section, String docString) {
        // get rid of leading/trailing spaces from doc string
        final String trimmedDocString = Arrays.stream(docString.split("\n"))
            .map(String::trim)
            .collect(Collectors.joining("\n"));
        theActorInTheSpotlight().attemptsTo(
            // TODO when it doesnt match the report and log shows <<null>> as oracle value why???
            Ensure.that(ServerFormular.sectionSummary(section)).text().matches("is equal to or matches as regex",
                text -> {
                    try {
                        return text.equals(trimmedDocString) || text.toString().matches(trimmedDocString);
                    } catch (Exception e) {
                        log.error("Failed to compare text '" + text + "' and '" + trimmedDocString + "', e");
                        return false;
                    }
                })
        );
    }
}
