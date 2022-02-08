package de.gematik.test.tiger.admin.bdd.pages;

import java.util.List;
import java.util.stream.Collectors;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.targets.Target;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class AdminHomePage extends PageObject {

    public static Target WELCOME_CARD = Target.the("welcome card")
        .locatedBy(".server-content .welcome-card");

    public static Target dragHandleOfSidebarItem(String nodeName) {
        if (nodeName.equals("local_proxy")) {
            return Target.the("sidebar item for " + nodeName)
                .locatedBy("#sidebar_server_" + nodeName);
        } else {
            return Target.the("drag handle of sidebar item for " + nodeName)
                .locatedBy("#sidebar_server_" + nodeName + " i.draghandle");
        }
    }

    public static Target sidebarItemOf(String nodeName) {
        return Target.the("sidebar item for " + nodeName)
            .locatedBy("#sidebar_server_" + nodeName);
    }

    public static Target sidebarItems() {
        return Target.the("all sidebar items")
            .locatedBy(".server-container.sidebar .sidebar-item,.sidebar-local-proxy");
    }

    public static Target sidebarHeader() {
        return Target.the("sidebar header")
            .locatedBy(".server-container.sidebar .testenv-sidebar-header");
    }

    public static Target nodeFormulars() {
        return Target.the("all node formulars")
            .locatedBy(".server-container.server-content form.server-formular");
    }

    public static Target testenvMenu() {
        return Target.the("test environment menu drop down")
            .locatedBy("nav.navbar .menu-testenv");
    }

    public static Target testenvMenuItem(String itemName) {
        return Target.the("test environment menu item " + itemName)
            .locatedBy("nav.navbar a." + itemName);
    }

    public static Target yesButtonOnConfirmModal() {
        return Target.the("yes button on confirm modal")
            .locatedBy("div.modal.show button.btn-yes");
    }

    public static Target noButtonOnConfirmModal() {
        return Target.the("no button on confirm modal")
            .locatedBy("div.modal.show button.btn-no");
    }

    public static Question<Integer> theNumberOfNodes() {
        return Question.about("theNumberOfNodes").answeredBy(actor -> new AdminHomePage().getNodeCount());
    }

    public static Question<String> theLastFormularType() {
        return Question.about("theLastFormularType").answeredBy(actor ->
            ServerFormular.getLastFormular().getInputField("type").resolveFor(actor).getAttribute("value"));
    }

    public static Question<List<String>> getSidebarItemNameList() {
        return Question.about("list of items' names in sidebar").answeredBy(actor ->
            sidebarItems().resolveAllFor(actor).stream()
                .filter(WebElement::isDisplayed)
                .map(item -> item.findElement(By.cssSelector(".server-label")).getText())
                .collect(Collectors.toList()));
    }

    public static Question<List<String>> getFormularNameList() {
        return Question.about("list of nodes' names in formular list").answeredBy(actor ->
            nodeFormulars().resolveAllFor(actor).stream()
                .filter(WebElement::isDisplayed)
                .map(item -> item.findElement(By.cssSelector(".server-key")).getText())
                .collect(Collectors.toList()));
    }

    private int getNodeCount() {
        return getDriver().findElements(By.cssSelector(".server-content .server-formular")).size();
    }
}

