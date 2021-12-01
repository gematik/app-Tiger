package de.gematik.test.tiger.admin.bdd.actions;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import de.gematik.test.tiger.admin.bdd.pages.ServerFormular;
import de.gematik.test.tiger.common.context.TestContext;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.actions.SendKeys;
import net.serenitybdd.screenplay.ensure.Ensure;
import net.serenitybdd.screenplay.targets.Target;
import net.thucydides.core.annotations.Step;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.Keys;

public class SimpleListActions implements Task {

    // TODO replace this with actor abilities (focuses on form/list is stored within actor)
    static TestContext ctxt = new TestContext("tiger.admin.simpleList");

    private final Action action;
    private final int index;
    private final String itemText;
    private final boolean hitEnter;
    @SuppressWarnings("FieldCanBeLocal")
    private final String stepDescription;

    public SimpleListActions(String itemText, int index, boolean hitEnter, Action action) {
        this.itemText = itemText;
        this.index = index;
        this.hitEnter = hitEnter;
        this.action = action;
        this.stepDescription = action.descriptionOf(this);
    }

    // actions

    public static SimpleListActions addEntry(String itemText, boolean hitEnter) {
        return instrumented(SimpleListActions.class, itemText, 0, hitEnter, Action.addEntry);
    }

    public static SimpleListActions selectEntry(int index) {
        return instrumented(SimpleListActions.class, null, index, false, Action.selectEntry);
    }

    public static SimpleListActions setValueForActiveItemTo(String itemText, boolean hitEnter) {
        return instrumented(SimpleListActions.class, itemText, 0, hitEnter, Action.setCurrentEntry);
    }

    public static SimpleListActions pressEsc() {
        return instrumented(SimpleListActions.class, null, 0, false, Action.pressEsc);
    }

    public static SimpleListActions deletesActiveItem() {
        return instrumented(SimpleListActions.class, null, 0, false, Action.deleteActiveItem);
    }

    // questions

    public static Question<String> getValueOfItem(int index) {
        return Question.about("value of item with index " + index).answeredBy(
            actor -> getItemForList(index, ctxt.getString("listName")).resolveFor(actor).getText());
    }

    // targets

    private static String getFormXpath() {
        return "//form[@id='content_server_" + ctxt.getString("serverKey") + "']";
    }

    private static String getListGroupUl(String name) {
        return getFormXpath() + "//*[@name='" + name + "']";
    }

    public static Target getAddButtonForList(String name) {
        return Target.the("add button for list " + name)
            .locatedBy(getListGroupUl(name) + "/parent::div/parent::div//button[contains(@class, 'btn-list-add')]");
    }

    public static Target getDeleteButtonForList(String name) {
        return Target.the("delete button for list " + name)
            .locatedBy(getListGroupUl(name) + "/parent::div/parent::div//button[contains(@class, 'btn-list-delete')]");
    }

    public static Target getEditingLineForList(String name) {
        return Target.the("editing item for list " + name)
            .locatedBy(getListGroupUl(name) + "//span[contains(@class, 'editing')]");
    }

    public static Target getActiveItemForList(String name) {
        return Target.the("active item of list " + name)
            .locatedBy(getListGroupUl(name) + "//li[contains(@class, 'active')]/span");
    }

    public static Target getItemForList(int index, String name) {
        return Target.the("item with index " + index)
            .locatedBy("(" + getListGroupUl(name) + "//li)[" + index + "]/span");
    }

    public static Target getDragHandleForList(int index, String name) {
        return Target.the("item with index " + index)
            .locatedBy("(" + getListGroupUl(name) + "//li)[" + index + "]/i");
    }

    // implementation

    private <T extends Actor> void addEntry(T actor) {
        String listName = ctxt.getString("listName");
        Target editingLine = getEditingLineForList(listName);
        actor.attemptsTo(
            Click.on(getAddButtonForList(listName)),
            Ensure.that(editingLine).isDisplayed(),
            hitEnter ?
                SendKeys.of(itemText).into(editingLine).thenHit(Keys.ENTER) :
                SendKeys.of(itemText).into(editingLine)
        );
    }

    private <T extends Actor> void selectEntry(T actor) {
        actor.attemptsTo(
            Click.on(getDragHandleForList(index, ctxt.getString("listName")))
        );
    }

    private <T extends Actor> void setCurrentEntry(T actor) {
        Target listItem = getActiveItemForList(ctxt.getString("listName"));
        actor.attemptsTo(
            // Actions
            Ensure.that(listItem).isDisplayed(),
            Click.on(listItem),
            hitEnter ?
                Enter.theValue(itemText).into(listItem).thenHit(Keys.ENTER) :
                Enter.theValue(itemText).into(listItem)
        );
    }

    private void deleteActiveItem(Actor actor) {
        Target listItem = getActiveItemForList(ctxt.getString("listName"));
        actor.attemptsTo(
            // Actions
            Ensure.that(listItem).isDisplayed(),
            Click.on(getDeleteButtonForList(ctxt.getString("listName")))
        );
    }

    public <T extends Actor> void pressEsc(T actor) {
        final ServerFormular form = new ServerFormular(ctxt.getString("serverKey"));
        actor.attemptsTo(
            SendKeys.of(Keys.ESCAPE).into(getEditingLineForList(ctxt.getString("listName")))
        );
    }

    @Override
    @Step("{0} #stepDescription")
    public <T extends Actor> void performAs(T actor) {
        action.execute(actor, this);
    }

    @RequiredArgsConstructor
    public enum Action {
        addEntry((actor, instance) -> instance.addEntry(actor)),
        selectEntry((actor, instance) -> instance.selectEntry(actor)),
        setCurrentEntry((actor, instance) -> instance.setCurrentEntry(actor)),
        pressEsc((actor, instance) -> instance.pressEsc(actor)),
        deleteActiveItem((actor, instance) -> instance.deleteActiveItem(actor));

        private final BiConsumer<Actor, SimpleListActions> actionConsumer;

        void execute(Actor actor, SimpleListActions instance) {
            actionConsumer.accept(actor, instance);
        }

        String descriptionOf(SimpleListActions instance) {
            switch (instance.action) {
                case addEntry:
                    return "adds a new entry " + instance.itemText + hitEnter(instance);
                case selectEntry:
                    return " clicks the drag handle of entry with index " + instance.index + " to select it";
                case setCurrentEntry:
                    return " sets the value for the current active entry to " + instance.itemText + hitEnter(instance);
                case pressEsc:
                    return " presses ESCAPE";
                case deleteActiveItem:
                    return " deletes active item";
                default:
                    throw new InvalidArgumentException("Unknown action " + instance.action);
            }
        }

        private String hitEnter(SimpleListActions instance) {
            return (instance.hitEnter ? " and hits ENTER" : "");
        }
    }
}
