package de.gematik.test.tiger.admin.bdd.pages;

import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.screenplay.Question;
import org.openqa.selenium.By;

public class AdminHomePage extends PageObject {

    public static Question<Integer> theNumberOfNodes() {
        return Question.about("theNumberOfNodes").answeredBy(actor -> new AdminHomePage().getNodeCount());
    }

    public static Question<String> theLastFormularType() {
        return Question.about("theLastFormularType").answeredBy(actor -> {
            ServerFormular form = ServerFormular.getLastFormular();
            return form.getInputField("type").resolveFor(actor).getAttribute("value");
        });
    }

    public int getNodeCount() {
        return getDriver().findElements(By.cssSelector(".server-content .server-formular")).size();
    }
}

