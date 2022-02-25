/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package drivers;

import io.cucumber.junit.CucumberOptions;
import net.serenitybdd.cucumber.CucumberWithSerenity;
import org.junit.runner.RunWith;

@RunWith(CucumberWithSerenity.class)
@CucumberOptions(
    features = {"./src/test/resources/features/tiger-test-lib/testcontext.feature"},
    plugin = {"json:target/cucumber-parallel/1.json"},
    glue = { "de.gematik.test.tiger.glue" })
public class Parallel1IT {
}
