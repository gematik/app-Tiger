package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.Then;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.serenitybdd.screenplay.ensure.Ensure;

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
                        return false;
                    }
                })
        );
    }
}
