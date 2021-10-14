package de.gematik.test.tigertest;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"src/test/resources/features/test.feature"},
    plugin = {"json:target/cucumber-parallel/1.json"},
    monochrome = false,
    glue = {"de.gematik.test.tiger.glue", "de.gematik.test.tiger.hooks"})
public class JunitBDDDriver {
}
