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
            Ensure.that(AdminHomePage.testenvMenuItem("btn-new-testenv")).isDisabled(),
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
