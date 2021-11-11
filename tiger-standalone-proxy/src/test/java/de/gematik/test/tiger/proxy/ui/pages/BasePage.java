package de.gematik.test.tiger.proxy.ui.pages;


import lombok.Getter;
import net.serenitybdd.core.pages.PageObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;


public abstract class BasePage extends PageObject {
    protected BasePage(String url) {
        this.url = url;
    }

    @Getter
    protected String url;

    public void openPage() {
        getDriver().get(url);
    }

    public WebElement elemX(String locatorStr) {
        return getDriver().findElement(By.xpath(locatorStr));
    }
}

