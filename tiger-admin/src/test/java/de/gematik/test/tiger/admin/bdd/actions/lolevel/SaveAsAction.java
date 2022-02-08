package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import java.io.File;
import java.time.Duration;
import lombok.SneakyThrows;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.openqa.selenium.Keys;

public class SaveAsAction implements Performable {

    public static final Target INPUT_SAVE_AS = Target.the("file-navigation-save")
        .locatedBy("//*[@id='file-navigation-modal']//input[@name='file-navigation-save']");
    public static final Target MENU_ITEM_SAVE_AS = AdminHomePage.testenvMenuItem("btn-save-as-testenv");
    public static final Target BTN_SAVE_AS = Target.the("Save as")
        .locatedBy(".btn-filenav-ok");
    public static final Target INPUT_FILE_NAVIGATION = Target.the("Path")
        .locatedBy("//*[@id='file-navigation-modal']//input[@name='file-navigation-path']");

    private final boolean submitFormViaEnter;
    private final String newSaveName;

    public SaveAsAction(String newSaveName, boolean submitFormViaEnter) {
        this.newSaveName = newSaveName;
        this.submitFormViaEnter = submitFormViaEnter;
    }

    public static SaveAsAction ofTypeVia(String newSaveName, boolean submitFormViaEnter) {
        return instrumented(SaveAsAction.class, newSaveName, submitFormViaEnter);
    }

    @SneakyThrows
    @Override
    @Step("{0} picks a name for the the test env configuration and saves it")
    public <T extends Actor> void performAs(T t) {
        t.attemptsTo(
            // Precondition
            Click.on(AdminHomePage.testenvMenu()),
            Ensure.that(MENU_ITEM_SAVE_AS).isEnabled(),
            // Actions
            Click.on(MENU_ITEM_SAVE_AS),
            Pause.pauseFor(1000), // as the modal is fading in 500ms
            Click.on(INPUT_SAVE_AS)
        );
        String filePath = INPUT_FILE_NAVIGATION.resolveFor(t).getValue();
        t.remember("filepath", filePath);
        t.attemptsTo(
            submitFormViaEnter ?
                Enter.theValue(newSaveName).into(INPUT_SAVE_AS)
                    .thenHit(Keys.ENTER) :
                Enter.theValue(newSaveName).into(INPUT_SAVE_AS).then(Click.on(BTN_SAVE_AS)),
            // Verification
            Ensure.that(
                    PerformActionsOnSnack.snackWithTextContaining(
                            "Saved configuration to " + filePath + File.separator + newSaveName)
                        .waitingForNoMoreThan(Duration.ofSeconds(3)))
                .isDisplayed(),
            PerformActionsOnSnack.closeSnack()
        );
    }
}
