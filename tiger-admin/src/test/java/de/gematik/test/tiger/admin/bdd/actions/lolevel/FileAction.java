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

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.AdminHomePage;
import java.io.File;
import java.time.Duration;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.Keys;

public class FileAction implements Performable {


    public static final Target INPUT_SAVE_AS = Target.the("File name input field")
        .locatedBy("//*[@id='file-navigation-modal']//input[@name='file-navigation-save']");
    public static final Target MENU_ITEM_SAVE_AS = AdminHomePage.testenvMenuItem("btn-save-as-testenv");
    public static final Target MENU_ITEM_LOAD = AdminHomePage.testenvMenuItem("btn-open-testenv");
    public static final Target BTN_SAVE_AS = Target.the("Save as button")
        .locatedBy(".btn-filenav-ok");
    public static final Target INPUT_FILE_NAVIGATION = Target.the("Path field")
        .locatedBy("//*[@id='file-navigation-modal']//input[@name='file-navigation-path']");
    public static final Target ALERT_VALIDATION_WRONG_FILENAME = Target.the("Error dialog")
        .locatedBy("//*[@id='modal-1']//div[contains(@class, 'modal-header') and contains(@class, 'bg-danger')]/h5[text()='Error']");
    public static final Target BTN_CANCEL = Target.the("Cancel button in the file dialog")
        .locatedBy(".btn-filenav-cancel");
    public static final Target MODAL_FILE_NAV = Target.the("File dialog")
        .locatedBy("//*[@id='file-navigation-modal']/div/div");

    public static Target getFileEntry(String filename) {
        return Target.the("File entry " + filename).locatedBy("//*[@id='file-navigation-modal']//div[@class='cfgfile text-success' and text()='" + filename+ "']");
    }

    private final boolean submitFormViaEnter;
    private final String fileName;
    private final boolean cancelFileAction;
    private final Action action;

    public FileAction(Action action, String fileSaveName, boolean submitFormViaEnter, boolean cancelTheSaving) {
        this.action = action;
        this.fileName = fileSaveName;
        this.submitFormViaEnter = submitFormViaEnter;
        this.cancelFileAction = cancelTheSaving;
    }

    public static FileAction saveToFile(String newSaveName, boolean submitFormViaEnter, boolean cancelTheSaving) {
        return instrumented(FileAction.class, Action.saveFile, newSaveName, submitFormViaEnter, cancelTheSaving);
    }

    public static FileAction loadFromFile(String fileName, boolean cancelLoading) {
        return instrumented(FileAction.class, Action.loadFile, fileName, false, cancelLoading);
    }


    @Override
    @Step("{0} #stepDescription")
    public <T extends Actor> void performAs(T actor) {
        action.execute(actor, this);
    }

    public void saveTestEnv(Actor t) {
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
        if (cancelFileAction) {
            t.attemptsTo(
                Click.on(BTN_CANCEL),
                Pause.pauseFor(1000),
                Ensure.that(MODAL_FILE_NAV).isNotDisplayed()
            );
        } else {
            t.attemptsTo(
                Ensure.that(INPUT_FILE_NAVIGATION).attribute("readonly").isEqualTo("true"),
                submitFormViaEnter ?
                    Enter.theValue(fileName).into(INPUT_SAVE_AS)
                        .thenHit(Keys.ENTER) :
                    Enter.theValue(fileName).into(INPUT_SAVE_AS).then(Click.on(BTN_SAVE_AS)),
                // Verification
                fileName.endsWith(".yaml") && fileName.length() > ".yaml".length() ?
                    Ensure.that(
                            PerformActionsOnSnack.snackWithTextContaining(
                                    "Saved configuration to " + filePath + File.separator + fileName)
                                .waitingForNoMoreThan(Duration.ofSeconds(3)))
                        .isDisplayed() :
                    Ensure.that(ALERT_VALIDATION_WRONG_FILENAME).isDisplayed()
            );
        }
    }

    public void loadTestEnv(Actor t) {
        t.attemptsTo(
            // Precondition
            Click.on(AdminHomePage.testenvMenu()),
            Ensure.that(MENU_ITEM_LOAD).isEnabled(),
            // Actions
            Click.on(MENU_ITEM_LOAD),
            Pause.pauseFor(1000) // as the modal is fading in 500ms
        );
        if (cancelFileAction) {
            t.attemptsTo(
                Click.on(BTN_CANCEL),
                Pause.pauseFor(1000),
                Ensure.that(MODAL_FILE_NAV).isNotDisplayed()
            );
        } else {
            t.attemptsTo(
                Click.on(getFileEntry(fileName)),
                // Verification
                fileName.endsWith(".yaml") && fileName.length() > ".yaml".length() ?
                    Ensure.that(
                            PerformActionsOnSnack.snackWithTextContaining(
                                    "Loaded yaml file")
                                .waitingForNoMoreThan(Duration.ofSeconds(3)))
                        .isDisplayed() :
                    Ensure.that(ALERT_VALIDATION_WRONG_FILENAME).isDisplayed()
            );
        }
    }

    @RequiredArgsConstructor
    public enum Action {
        saveFile((actor, instance) -> instance.saveTestEnv(actor)),
        loadFile((actor, instance) -> instance.loadTestEnv(actor));

        private final BiConsumer<Actor, FileAction> actionConsumer;

        void execute(Actor actor, FileAction instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(FileAction instance) {
            switch (instance.action) {
                case saveFile:
                    return "saves test env to file " + instance.fileName;
                case loadFile:
                    return " loads test env from " + instance.fileName;
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }
    }
}
