package de.gematik.test.tiger.admin.bdd.actions.lolevel;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.*;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

public class PerformActionsOnList implements Task {

    private final Action action;
    private final int row;
    private final String itemText;
    private final boolean hitEnter;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final String stepDescription;

    public PerformActionsOnList(String itemText, int row, boolean hitEnter, Action action) {
        this.itemText = itemText;
        this.row = row;
        this.hitEnter = hitEnter;
        this.action = action;
        this.stepDescription = action.descriptionOf(this);
    }

    @SuppressWarnings("unused")
    public PerformActionsOnList(int row, Action action) {
        this(null, row, false, action);
    }


    @SuppressWarnings("unused")
    public PerformActionsOnList(String itemText, boolean hitEnter, Action action) {
        this(itemText, -1, hitEnter, action);
    }

    @SuppressWarnings("unused")
    public PerformActionsOnList(Action action) {
        this(null, -1, false, action);
    }

    // actions

    public static PerformActionsOnList addsItem(String itemText, boolean hitEnter) {
        return instrumented(PerformActionsOnList.class, itemText, hitEnter, Action.addsItem);
    }

    public static PerformActionsOnList selectsItem(int row) {
        return instrumented(PerformActionsOnList.class, row, Action.selectsItem);
    }

    public static PerformActionsOnList setsValueForActiveItemTo(String itemText, boolean hitEnter) {
        return instrumented(PerformActionsOnList.class, itemText, hitEnter, Action.setsValueForActiveItemTo);
    }

    public static PerformActionsOnList pressesEsc() {
        return instrumented(PerformActionsOnList.class, Action.pressesEsc);
    }

    public static PerformActionsOnList deletesActiveItem() {
        return instrumented(PerformActionsOnList.class, Action.deletesActiveItem);
    }

    public static PerformActionsOnList addsComplexItem(String docString, boolean apply) {
        return instrumented(PerformActionsOnList.class, docString, apply, Action.addsComplexItem);
    }

    public static Performable setsValueForActiveComplexItemTo(String docString, boolean apply) {
        return instrumented(PerformActionsOnList.class, docString, apply, Action.setsValueForActiveComplexItemTo);
    }

    // questions

    public static Question<String> getValueOfItem(int index) {
        return Question.about("value of item with index " + index).answeredBy(
            actor -> listItemInRow(index).resolveFor(actor).getText());
    }

    public static Question<Integer> getListSize() {
        return Question.about("length of list")
            .answeredBy(actor -> ServerFormular.getInputField(actor, actor.recall("listName"))
                .resolveFor(actor).findElements(By.cssSelector("li")).size()
            );
    }

    public static Question<Integer> askForActiveItemIndex() {
        return Question.about("delete button of list")
            .answeredBy(actor -> {
                WebElement[] items = ServerFormular.getInputField(actor, actor.recall("listName"))
                    .resolveFor(actor).findElements(By.cssSelector("li")).toArray(new WebElement[0]);
                for (int i = 0; i < items.length; i++) {
                    if (items[i].getAttribute("class").contains("active")) {
                        return i + 1;
                    }
                }
                return -1;
            });
    }

    // targets

    public static Target listAddButton() {
        return Target.the("add button for list " + theActorInTheSpotlight().recall("listName"))
            .locatedBy(listGroupUlXpath() + "/parent::div/parent::div//button[contains(@class, 'btn-list-add')]");
    }

    public static Target listDeleteButtonOfActiveItem() {
        return Target.the("delete button for active item of list " + theActorInTheSpotlight().recall("listName"))
            .locatedBy(activeListItem().getCssOrXPathSelector() + "/../i[contains(@class, 'btn-list-delete')]");
    }

    public static Target listApplyButton() {
        return Target.the("apply button for list " + theActorInTheSpotlight().recall("listName"))
            .locatedBy(listGroupUlXpath() + "/parent::div/parent::div/parent::fieldset//button[contains(@class, 'btn-list-apply')]");
    }

    public static Target listEditingRow() {
        return Target.the("editing item for list " + theActorInTheSpotlight().recall("listName"))
            .locatedBy(listGroupUlXpath() + "//span[contains(@class, 'editing')]");
    }

    public static Target activeListItem() {
        return Target.the("active item of list " + theActorInTheSpotlight().recall("listName"))
            .locatedBy(listGroupUlXpath() + "//li[contains(@class, 'active')]/span");
    }

    public static Target listItemInRow(int index) {
        return Target.the("item with index " + index)
            .locatedBy("(" + listGroupUlXpath() + "//li)[" + index + "]/span");
    }

    public static Target dragHandleOfItemInRow(int index) {
        return Target.the("item with index " + index)
            .locatedBy("(" + listGroupUlXpath() + "//li)[" + index + "]/i[contains(@class,'draghandle')]");
    }

    private static String formXpath() {
        return "//form[@id='content_server_" + theActorInTheSpotlight().recall("serverKey") + "']";
    }

    private static String listGroupUlXpath() {
        return formXpath() + "//*[@name='" + theActorInTheSpotlight().recall("listName") + "']";
    }

    // implementation

    private <T extends Actor> void addsItem(T actor) {
        Target editingLine = listEditingRow();
        actor.attemptsTo(
            Click.on(listAddButton()),
            Ensure.that(editingLine).isDisplayed(),
            hitEnter ?
                SendKeys.of(itemText).into(editingLine).thenHit(Keys.ENTER) :
                SendKeys.of(itemText).into(editingLine)
        );
    }

    private <T extends Actor> void selectsItem(T actor) {
        actor.attemptsTo(
            Click.on(dragHandleOfItemInRow(row))
        );
    }

    private <T extends Actor> void setsValueForActiveItemTo(T actor) {
        Target listItem = activeListItem();
        actor.attemptsTo(
            // Actions
            Ensure.that(listItem).isDisplayed(),
            Click.on(listItem),
            hitEnter ?
                Enter.theValue(itemText).into(listItem).thenHit(Keys.ENTER) :
                Enter.theValue(itemText).into(listItem)
        );
    }

    public <T extends Actor> void pressesEsc(T actor) {
        actor.attemptsTo(
            SendKeys.of(Keys.ESCAPE).into(listEditingRow())
        );
    }

    private void deletesActiveItem(Actor actor) {
        Target listItem = activeListItem();
        actor.attemptsTo(
            // Actions
            Ensure.that(listItem).isDisplayed(),
            Click.on(listDeleteButtonOfActiveItem())
        );
    }

    private void addsComplexItem(Actor actor) {
        String listName = actor.recall("listName");
        Map<String, String> fields = Arrays.stream(itemText.split("\n")).
            collect(Collectors.toMap(line -> StringUtils.substringBefore(line, ":").trim(),
                line -> StringUtils.substringAfter(line, ":").trim()));

        List<Performable> actions = new ArrayList<>();
        actions.add(Click.on(listAddButton()));
        fields.forEach((key, value) -> actions.add(
            Ensure.that(ServerFormular.getInputField(actor, listName + "." + key)).isEnabled()));
        fields.forEach((key, value) -> actions.add(
            Enter.theValue(value).into(ServerFormular.getInputField(actor, listName + "." + key))));
        if (hitEnter) {
            actions.add(Scroll.to(listApplyButton()).andAlignToTop());
            actions.add(Click.on(listApplyButton()));
        }
        actor.attemptsTo(actions.toArray(new Performable[0]));
    }

    private void setsValueForActiveComplexItemTo(Actor actor) {
        String listName = actor.recall("listName");
        Map<String, String> fields = Arrays.stream(itemText.split("\n")).
            collect(Collectors.toMap(line -> StringUtils.substringBefore(line, ":").trim(),
                line -> StringUtils.substringAfter(line, ":").trim()));
        List<Performable> actions = new ArrayList<>();
        fields.forEach((key, value) -> actions.add(
            Enter.theValue(value).into(ServerFormular.getInputField(actor, listName + "." + key))));
        if (hitEnter) {
            actions.add(Click.on(listApplyButton()));
        }
        actor.attemptsTo(actions.toArray(new Performable[0]));
    }

    @Override
    @Step("{0} #stepDescription")
    public <T extends Actor> void performAs(T actor) {
        action.execute(actor, this);
    }

    @RequiredArgsConstructor
    public enum Action {
        addsItem((actor, instance) -> instance.addsItem(actor)),
        selectsItem((actor, instance) -> instance.selectsItem(actor)),
        setsValueForActiveItemTo((actor, instance) -> instance.setsValueForActiveItemTo(actor)),
        pressesEsc((actor, instance) -> instance.pressesEsc(actor)),
        deletesActiveItem((actor, instance) -> instance.deletesActiveItem(actor)),
        addsComplexItem((actor, instance) -> instance.addsComplexItem(actor)),
        setsValueForActiveComplexItemTo((actor, instance) -> instance.setsValueForActiveComplexItemTo(actor));

        private final BiConsumer<Actor, PerformActionsOnList> actionConsumer;

        void execute(Actor actor, PerformActionsOnList instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(PerformActionsOnList instance) {
            switch (instance.action) {
                case addsItem:
                    return "adds a new item " + instance.itemText + hitEnter(instance);
                case selectsItem:
                    return " clicks the drag handle of item in row " + instance.row + " to select it";
                case setsValueForActiveItemTo:
                    return " sets the value for the active item to " + instance.itemText + hitEnter(instance);
                case pressesEsc:
                    return " presses ESCAPE";
                case deletesActiveItem:
                    return " deletes active item";
                case addsComplexItem:
                    return "adds a new complex item " + instance.itemText.replace("\n", ", ") + hitEnter(instance);
                case setsValueForActiveComplexItemTo:
                    return " sets the value for the active complex item to " + instance.itemText.replace("\n", ", ") + hitEnter(instance);
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }

        private String hitEnter(PerformActionsOnList instance) {
            return (instance.hitEnter ? " and hits ENTER" : "");
        }

    }
}
