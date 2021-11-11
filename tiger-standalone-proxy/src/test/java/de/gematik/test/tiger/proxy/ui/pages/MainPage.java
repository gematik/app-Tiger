package de.gematik.test.tiger.proxy.ui.pages;


import de.gematik.test.tiger.proxy.ui.UiTest;

import java.util.concurrent.TimeUnit;

public class MainPage extends BasePage {
    public MainPage() {
        super("http://127.0.0.1:" + UiTest.getAdminPort() + "/webui");
        getDriver().manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
    }

    public void assertThatAllMandatoryElementsVisible() {
        shouldBeVisible(elemX("//span[contains(.,'Flow')]"));
    }
}