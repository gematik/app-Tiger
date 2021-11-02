package de.gematik.test.tiger.proxy.pages;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class MainPage extends BasePage {
    public MainPage() {
        super("http://127.0.0.1:8080/webui");
        getDriver().manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
    }

    public void assertThatAllMandatoryElementsVisible() {
        shouldBeVisible(elemX("//span[contains(.,'Flow')]"));
    }
}