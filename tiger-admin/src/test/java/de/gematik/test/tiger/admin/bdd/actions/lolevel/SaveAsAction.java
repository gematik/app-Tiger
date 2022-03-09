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
    public static final Target ALERT_VALIDATION_WRONG_FILENAME = Target.the("bg-danger")
        .locatedBy("//*[@id='modal-1']/div/div/div[2]/div");
    public static final Target BTN_CANCEL = Target.the("btn-filenav-cancel")
        .locatedBy(".btn-filenav-cancel");
    public static final Target MODAL_FILE_NAV = Target.the("file-navigation-modal")
        .locatedBy("//*[@id='file-navigation-modal']/div/div");

    private final boolean submitFormViaEnter;
    private final String newSaveName;
    private final boolean cancelTheSaving;

    public SaveAsAction(String newSaveName, boolean submitFormViaEnter, boolean cancelTheSaving) {
        this.newSaveName = newSaveName;
        this.submitFormViaEnter = submitFormViaEnter;
        this.cancelTheSaving = cancelTheSaving;
    }

    public static SaveAsAction ofTypeVia(String newSaveName, boolean submitFormViaEnter, boolean cancelTheSaving) {
        return instrumented(SaveAsAction.class, newSaveName, submitFormViaEnter, cancelTheSaving);
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
        if (cancelTheSaving) {
            t.attemptsTo(
                Click.on(BTN_CANCEL),
                Pause.pauseFor(1000),
                Ensure.that(MODAL_FILE_NAV).isNotDisplayed()
            );
        } else {
            t.attemptsTo(
                Ensure.that(INPUT_FILE_NAVIGATION).attribute("readonly").isEqualTo("true"),
                submitFormViaEnter ?
                    Enter.theValue(newSaveName).into(INPUT_SAVE_AS)
                        .thenHit(Keys.ENTER) :
                    Enter.theValue(newSaveName).into(INPUT_SAVE_AS).then(Click.on(BTN_SAVE_AS)),
                // Verification
                newSaveName.endsWith(".yaml") && newSaveName.length() > ".yaml".length() ?
                    Ensure.that(
                            PerformActionsOnSnack.snackWithTextContaining(
                                    "Saved configuration to " + filePath + File.separator + newSaveName)
                                .waitingForNoMoreThan(Duration.ofSeconds(3)))
                        .isDisplayed() :
                    Ensure.that(ALERT_VALIDATION_WRONG_FILENAME).isDisplayed()
            );
        }
    }
}
