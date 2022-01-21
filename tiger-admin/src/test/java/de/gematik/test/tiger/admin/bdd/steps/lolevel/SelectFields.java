package de.gematik.test.tiger.admin.bdd.steps.lolevel;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import io.cucumber.java.en.And;
import java.util.List;
import java.util.stream.Collectors;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.questions.SelectedValue;
import net.serenitybdd.screenplay.questions.SelectedValues;
import net.serenitybdd.screenplay.targets.Target;
import net.serenitybdd.screenplay.ui.Select;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class SelectFields {

    @And("she/he checks select field {string} contains {string}")
    public void checksSelectFieldContains(String fieldName, String optionCSV) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), fieldName);
        List<String> options = List.of(optionCSV.split(","));
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget.resolveFor(theActorInTheSpotlight()).findElements(By.cssSelector("option")).stream()
                .map(WebElement::getText)
                .collect(Collectors.toList())).hasSameSizeAs(options),
            Ensure.that(fieldTarget.resolveFor(theActorInTheSpotlight()).findElements(By.cssSelector("option")).stream()
                .map(e -> e.getAttribute("innerHTML")) // getText() returns "" as the option elements are hidden
                .collect(Collectors.toList())).containsOnlyElementsFrom(options)
        );
    }

    // selects the option by visible text!!!
    @And("she/he selects entry {string} in select field {string}")
    public void selectsEntryInSelectField(String entry, String fieldName) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Select.option(entry).from(fieldTarget)
        );
    }

    // selects the option by visible text!!!
    @And("she/he selects entry {string} in multi select field {string}")
    public void selectsEntryInMultiSelectField(String entry, String fieldName) {
        Target fieldTarget = ServerFormular.getMultiSelectField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Click.on(fieldTarget),
            Click.on(ServerFormular.getMultiSelectEntry(fieldName, entry))
        );
    }

    @And("she/he checks select field {string} has entry {string} selected")
    public void checksSelectFieldHasEntrySelected(String fieldName, String entry) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget).hasSelectedVisibleText(entry)
        );
    }

    @And("she/he checks multi select field {string} has entry {string} selected")
    public void checksMultiSelectFieldHasEntrySelected(String fieldName, String entry) {
        Target fieldTarget = ServerFormular.getMultiSelectField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget).containsElements(By.xpath("./ul/li[contains(@class, 'badge')]/span[text()='" + entry + "']"))
        );
    }

    @And("he checks select field {string} has no entry selected")
    public void checksSelectFieldHasNoEntrySelected(String fieldName) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(SelectedValues.of(fieldTarget).answeredBy(theActorInTheSpotlight())).isEmpty()
        );
    }

    @And("he checks multi select field {string} has no entry selected")
    public void checksMultiSelectFieldHasNoEntrySelected(String fieldName) {
        Target fieldTarget = ServerFormular.getMultiSelectField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget).not().containsElements(By.xpath("./ul/li[contains(@class, 'badge')]"))
        );
    }

    @And("she/he checks select field {string} contains no entries")
    public void checksSelectFieldContainsNoEntries(String fieldName) {
        Target fieldTarget = ServerFormular.getInputField(theActorInTheSpotlight(), fieldName);
        theActorInTheSpotlight().attemptsTo(
            Ensure.that(fieldTarget).not().containsElements(".//option[text()!='']")
        );
    }
}
