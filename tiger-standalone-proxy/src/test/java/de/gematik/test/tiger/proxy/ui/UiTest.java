package de.gematik.test.tiger.proxy.ui;


import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "de.gematik.test.tiger.proxy.ui.glue",
    plugin = {
        "de.gematik.test.tiger.proxy.ui.MockServerPlugin",
        "de.gematik.test.tiger.proxy.ui.SpringBootStarterPlugin"
    })
@SpringBootTest
public class UiTest {
    public static int adminPort;
    public static int proxyPort;

    public static int getAdminPort() {
        return adminPort;
    }

    public static int getProxyPort() {
        return proxyPort;
    }
}
