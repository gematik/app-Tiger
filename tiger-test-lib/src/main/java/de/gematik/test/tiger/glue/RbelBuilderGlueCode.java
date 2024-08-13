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

package de.gematik.test.tiger.glue;

import static junit.framework.TestCase.assertEquals;

import de.gematik.rbellogger.builder.RbelBuilder;
import de.gematik.rbellogger.builder.RbelBuilderManager;
import de.gematik.rbellogger.builder.RbelObjectJexl;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelSerializationAssertion;
import de.gematik.rbellogger.writer.RbelContentType;
import de.gematik.test.tiger.lib.TigerDirector;
import io.cucumber.java.Before;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelBuilderGlueCode {

  private final RbelBuilderManager rbelBuilders = new RbelBuilderManager();

  @Before()
  public void beforeScenario() {
    TigerDirector.readConfiguration();
  }

  /**
   * Creates a new Rbel object with a given key and string content; the string can be a jexl
   * expression
   *
   * @param name key of Rbel object
   * @param content content of Rbel object, or jexl expression resolving to one
   */
  @Gegebensei(
      "TGR erstellt ein neues Rbel-Objekt {tigerResolvedString} mit Inhalt {tigerResolvedString}")
  @Given("TGR creates a new Rbel object {tigerResolvedString} with content {tigerResolvedString}")
  public void createFromContent(String name, String content) {
    rbelBuilders.put(name, RbelBuilder.fromString(content));
    RbelObjectJexl.initJexl(rbelBuilders);
  }

  /**
   * Creates a new empty Rbel object
   *
   * @param name key of Rbel object
   */
  @Gegebensei(
      "TGR erstellt ein neues leeres Rbel-Objekt {tigerResolvedString} mit Typ {rbelContentType}")
  @Given("TGR creates a new empty Rbel object {tigerResolvedString} of type {rbelContentType}")
  public void createFromScratch(String name, RbelContentType type) {
    rbelBuilders.put(name, RbelBuilder.fromScratch(type));
    RbelObjectJexl.initJexl(rbelBuilders);
  }

  /**
   * Sets a value of an object at a specified path; newValue is of type String
   *
   * @param objectName name of object in rbelBuilders
   * @param rbelPath path which is to be set
   * @param newValue new value to be set
   */
  @Wenn(
      "TGR setzt Rbel-Objekt {tigerResolvedString} an Stelle {tigerResolvedString} auf Wert"
          + " {tigerResolvedString}")
  @When(
      "TGR sets Rbel object {tigerResolvedString} at {tigerResolvedString} to new value"
          + " {tigerResolvedString}")
  public void setValueAt(String objectName, String rbelPath, String newValue) {
    RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
    rbelBuilder.setValueAt(rbelPath, newValue);
    logRbelBuilderChangesOptionally(objectName, rbelPath, newValue, rbelBuilder);
  }

  /**
   * Adds a new entry to an array or a list of a Rbel object at a specific path
   *
   * @param objectName name of Rbel object
   * @param rbelPath path of array/list
   * @param newEntry new entry
   */
  @Wenn(
      "TGR ergänzt Rbel-Objekt {tigerResolvedString} an Stelle {tigerResolvedString} um"
          + " {tigerResolvedString}")
  @When(
      "TGR extends Rbel object {tigerResolvedString} at path {tigerResolvedString} by a new entry"
          + " {tigerResolvedString}")
  public void addEntryAt(String objectName, String rbelPath, String newEntry) {
    RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
    rbelBuilder.addEntryAt(rbelPath, newEntry);
    logRbelBuilderChangesOptionally(objectName, rbelPath, newEntry, rbelBuilder);
  }

  /**
   * Asserts whether a string value at a given path of the rootTreeNode of a RbelBuilder is a
   * certain value
   *
   * @param objectName name of RbelBuilder in rbelBuilders Map
   * @param rbelPath Path to specific node
   * @param expectedValue value to be asserted
   */
  @Wenn(
      "TGR prüft, dass Rbel-Objekt {tigerResolvedString} an Stelle {tigerResolvedString} gleich"
          + " {tigerResolvedString} ist")
  @When(
      "TGR asserts Rbel object {tigerResolvedString} at {tigerResolvedString} equals"
          + " {tigerResolvedString}")
  public void assertValueAtEquals(String objectName, String rbelPath, String expectedValue) {
    RbelBuilder rbelBuilder = rbelBuilders.get(objectName);
    assertEquals(
        expectedValue,
        rbelBuilder.getTreeRootNode().findElement(rbelPath).orElseThrow().getRawStringContent());
  }

  /**
   * Asserts, if 2 Rbel object serializations are equal
   *
   * @param jexlExpressionActual actual value
   * @param jexlExpressionExpected expected value
   * @param contentType type of Rbel object content for comparison
   */
  @SneakyThrows
  @Wenn(
      "TGR prüft, dass {tigerResolvedString} gleich {tigerResolvedString} mit Typ {rbelContentType}"
          + " ist")
  @When("TGR asserts {tigerResolvedString} equals {tigerResolvedString} of type {rbelContentType}")
  public void assertJexlOutputEquals(
      String jexlExpressionActual, String jexlExpressionExpected, RbelContentType contentType) {
    RbelSerializationAssertion.assertEquals(
        jexlExpressionExpected, jexlExpressionActual, contentType);
  }

  private void logMessageOptionally(String message) {
    if (TigerDirector.getLibConfig().createRbelModificationReports) {
      log.info(message.translateEscapes());
    }
  }

  private void logRbelBuilderChangesOptionally(
      String objectName, String rbelPath, String newValue, RbelBuilder rbelBuilder) {
    logMessageOptionally(
        String.format("Changed Rbel object '%s' at '%s' to '%s'", objectName, rbelPath, newValue));
    RbelElement asRbelElement = new RbelElement(rbelBuilder.getTreeRootNode().getContent(), null);
    logMessageOptionally("New Object: %s".formatted(asRbelElement.printTreeStructure()));
  }
}
