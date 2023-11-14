/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.integrationtest;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.TigerCucumberRunner;
import org.junit.runner.RunWith;

@RunWith(TigerCucumberRunner.class)
@CucumberOptions(
    features = {"./src/test/resources/features/tiger-test-lib/readTrafficFiles.feature"},
    plugin = {"json:target/cucumber-parallel/3.json"},
    glue = {"de.gematik.test.tiger.glue"},
    tags = "not @Ignore")
public class TestReadTrafficFiles {}
