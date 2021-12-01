package de.gematik.test.tiger.admin.bdd;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"./src/test/resources/features/uiComponents/SimpleLists.feature"},
    plugin = {"de.gematik.test.tiger.admin.bdd.SpringBootStarterPlugin"},
    glue = {"de.gematik.test.tiger.admin.bdd.steps"})
@SpringBootTest()
public class SpringBootDriver {

    @LocalServerPort
    public static int adminPort;

    public static int getAdminPort() {
        return adminPort == 0 ? 8080 : adminPort;
    }
}
