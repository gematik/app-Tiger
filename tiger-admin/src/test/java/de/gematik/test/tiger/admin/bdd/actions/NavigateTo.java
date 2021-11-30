package de.gematik.test.tiger.admin.bdd.actions;

import de.gematik.test.tiger.admin.bdd.SpringBootDriver;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Open;

@Slf4j
public class NavigateTo {

    public static Performable adminUIHomePage() {
        return Task.where("{0} opens the Admin UI home page",
            Open.url("http://127.0.0.1:" + SpringBootDriver.getAdminPort())
        );
    }
}
