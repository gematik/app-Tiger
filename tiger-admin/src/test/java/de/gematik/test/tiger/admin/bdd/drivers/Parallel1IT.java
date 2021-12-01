package de.gematik.test.tiger.admin.bdd.drivers;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"./src/test/resources/features"},
    plugin = {"json:target/cucumber-parallel/1.json"},
    glue = { "de.gematik.test.tiger.admin.bdd.steps" })
public class Parallel1IT {
}
