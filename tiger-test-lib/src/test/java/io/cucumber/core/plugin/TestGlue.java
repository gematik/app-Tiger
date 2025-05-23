/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package io.cucumber.core.plugin;

import de.gematik.test.tiger.glue.ResolvableArgument;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;

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
