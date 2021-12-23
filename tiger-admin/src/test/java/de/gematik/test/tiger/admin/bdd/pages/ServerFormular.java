package de.gematik.test.tiger.admin.bdd.pages;

import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import java.util.List;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.targets.Target;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ServerFormular extends PageObject {

    private final String serverKey;

    private ServerFormular() {
        List<WebElement> forms = getDriver().findElements(By.cssSelector(".server-content .server-formular"));
        assertThat(forms).isNotEmpty();
        serverKey = forms.get(forms.size() - 1).findElement(By.cssSelector("span.server-key")).getText();
    }

    public ServerFormular(String serverKey) {
        this.serverKey = serverKey;
    }

    public static ServerFormular getLastFormular() {
        return new ServerFormular();
    }

    public static String xPath() {
        return "//form[@id='content_server_" + theActorInTheSpotlight().recall("serverKey") + "']";
    }

    public static String css(Actor actor) {
        return "#content_server_" + actor.recall("serverKey");
    }

    public static Target getInputField(Actor actor, String name) {
        return Target.the("input field " + name)
            .locatedBy(css(actor) + " *[name='" + name + "']");
    }
    public static Target getMultiSelectField(Actor actor, String name) {
        return Target.the("multi select field " + name)
            .locatedBy(css(actor) + " *[name='" + name + "'] + div.dashboardcode-bsmultiselect");
    }

    public static Target getMultiSelectEntry(String fieldName, String entryText) {
        return Target.the("entry '" + entryText + "' of input field " + fieldName)
            .located(By.xpath(xPath() + "//*[@name='" + fieldName + "']/following::div[1]"
                + "//label[contains(@class,'form-check-label') and text()='" + entryText + "']"));
    }



    public static Target getSection(String section) {
        return Target.the("section " + section)
            .locatedBy(css(theActorInTheSpotlight()) + " fieldset[section='" + section + "']");
    }


    public static Target sectionSummary(String section) {
        return Target.the("summary of section " + section)
            .locatedBy(css(theActorInTheSpotlight()) + " fieldset[section='" + section + "'] .fieldset-summary");
    }

    public static Target getHeading() {
        return Target.the("heading of formular")
            .locatedBy(css(theActorInTheSpotlight()) + " .server-key");
    }

    public static Target getHeading(String nodeName) {
        return Target.the("heading of formular " + nodeName)
            .locatedBy("#content_server_" + nodeName + " .server-key");
    }

    public static Target globalAdvancedIcon(Actor actor) {
        return Target.the("global advanced icon")
            .locatedBy(css(actor) + " .btn-advanced.global");
    }

    public static Target advancedIconOfSection(Actor actor, String section) {
        return Target.the("global advanced icon")
            .locatedBy(css(actor) + " fieldset[section='" + section + "'] .btn-advanced");
    }

    public static Target sidebarItem(Actor actor) {
        return Target.the("sidebar item of node " + actor.recall("serverKey"))
            .locatedBy("#sidebar_server_" + actor.recall("serverKey"));
    }

    public static Target sidebarHeader(Actor actor) {
        return Target.the("sidebar item of node " + actor.recall("serverKey"))
            .locatedBy("#sidebar_server_" + actor.recall("serverKey"));
    }

    public static Target sidebarItemContextMenu(String nodeName) {
        return Target.the("context menu in sidebar of node " + nodeName)
            .locatedBy("#sidebar_server_" +nodeName + " .context-menu-one");
    }

    public static Target sidebarItemContextMenuEntry(String entry) {
        return Target.the("context menu entry " + entry)
            .locatedBy(".context-menu-list .ctxt-" + entry);
    }

    public static Target nodeCollapseIcon(Actor actor) {
        return Target.the("global advanced icon")
            .locatedBy(css(actor) + " .server-formular-collapse-icon");
    }

    public Target getInputField(String name) {
        return Target.the("input field " + name)
            .locatedBy("#content_server_" + serverKey + " *[name='" + name + "']");
    }
}
