package de.gematik.test.tiger.admin.bdd.pages;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.screenplay.targets.Target;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ServerFormular extends PageObject {

    private String serverKey;

    private String formXpath;

    private ServerFormular() {
        List<WebElement> forms = getDriver().findElements(By.cssSelector(".server-content .server-formular"));
        assertThat(forms).isNotEmpty();
        serverKey = forms.get(forms.size() - 1).findElement(By.cssSelector("span.server-key")).getText();
        formXpath = "//form[@id='content_server_" + serverKey + "']";
    }

    public ServerFormular(String serverKey) {
        this.serverKey = serverKey;
        formXpath = "//form[@id='content_server_" + serverKey + "']";
    }

    public static ServerFormular getLastFormular() {
        return new ServerFormular();
    }

    public static ServerFormular forKey(String serverKey) {
        return new ServerFormular(serverKey);
    }

    public Target getInputField(String name) {
        return Target.the("input field " + name).locatedBy("#content_server_" + serverKey + " *[name='" + name + "']");
    }

    public Target getTabLink(String tabName) {
        return Target.the("link for tab " + tabName)
            .locatedBy(formXpath + "//a[contains(@class,'nav-link') and text()='" + tabName + "']");
    }

    public Target getSectionLegend(String section) {
        return Target.the("legend for section " + section)
            .locatedBy(formXpath + "//fieldset[@section='" + section + "']/legend");
    }

}
