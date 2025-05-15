/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib.integrationtest;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import de.gematik.test.tiger.glue.ResolvableArgument;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @Ignore")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "de.gematik.test.tiger.glue")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "io.cucumber.core.plugin.TigerSerenityReporterPlugin")
public class TestGlue {

  @When("test step resolves {tigerResolvedString} and does not resolve {string}")
  public void testGlueMethod(String resolved, String unresolved) {
    // This is a dummy method to test the glue code
    // The actual implementation is not relevant for this test
    System.out.println("Resolved: " + resolved);
    System.out.println("Unresolved: " + unresolved);
  }

  @When("step has a resolvable datatable:")
  @ResolvableArgument
  public void testDatatable(DataTable dataTable) {}

  @When("step has a resolvable docstring:")
  @ResolvableArgument
  public void testDocString(String docString) {}

  @When("step has a non resolvable datatable:")
  public void testUnresolvableDatatable(DataTable dataTable) {
    // This is a dummy method to test the glue code
    // The actual implementation is not relevant for this test
    System.out.println("Unresolvable DataTable: " + dataTable);
  }

  @When("step has a non resolvable docstring:")
  public void testUnresolvableDocString(String docString) {
    // This is a dummy method to test the glue code
    // The actual implementation is not relevant for this test
    System.out.println("Unresolvable DocString: " + docString);
  }
}
