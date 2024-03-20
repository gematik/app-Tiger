/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.integrationtest;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.TigerCucumberRunner;
import org.junit.runner.RunWith;

@RunWith(TigerCucumberRunner.class)
@CucumberOptions(
    features = {"./src/test/resources/features/tiger-test-lib/serverUpDown.feature"},
    glue = {"de.gematik.test.tiger.glue"},
    tags = "not @Ignore")
public class TestServerUpDownGlueCode {}
