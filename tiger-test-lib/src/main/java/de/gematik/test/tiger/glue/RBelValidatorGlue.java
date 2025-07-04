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
package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.annotation.ResolvableArgument;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.rbel.*;
import de.gematik.test.tiger.lib.rbel.ModeType;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.xmlunit.builder.DiffBuilder;

@Slf4j
@Getter
public class RBelValidatorGlue {

  private final RbelMessageRetriever rbelMessageRetriever;
  private final RbelValidator rbelValidator;

  public RBelValidatorGlue(RbelMessageRetriever rbelMessageRetriever) {
    this.rbelMessageRetriever = rbelMessageRetriever;
    this.rbelValidator = new RbelValidator();
  }

  public RBelValidatorGlue() {
    this(RbelMessageRetriever.getInstance());
  }

  @BeforeAll
  public static void checktestenv() {
    TigerDirector.assertThatTigerIsInitialized();
  }

  // =================================================================================================================
  //
  //    R E Q U E S T   F I L T E R I N G
  //
  // =================================================================================================================

  /**
   * Specify the amount of seconds Tiger should wait when filtering for requests / responses before
   * reporting them as not found.
   */
  @Gegebensei("TGR setze Anfrage Timeout auf {int} Sekunden")
  @Given("TGR set request wait timeout to {int} seconds")
  public void tgrSetRequestWaitTimeout(final int waitsec) {
    TigerGlobalConfiguration.putValue(
        "tiger.rbel.request.timeout", waitsec, ConfigurationValuePrecedence.TEST_CONTEXT);
  }

  /**
   * clear all validatable rbel messages. This does not clear the recorded messages later on
   * reported via the rbel log HTML page or the messages shown on web ui of tiger proxies.
   */
  @Wenn("TGR lösche aufgezeichnete Nachrichten")
  @When("TGR clear recorded messages")
  public void tgrClearRecordedMessages() {
    rbelMessageRetriever.clearRbelMessages();
  }

  /**
   * filter all subsequent findRequest steps for hostname. To reset set host name to empty string
   * "".
   *
   * @param hostname host name (regex supported) to filter for
   */
  @Wenn("TGR filtere Anfragen nach Server {tigerResolvedString}")
  @When("TGR filter requests based on host {tigerResolvedString}")
  public void tgrFilterBasedOnHost(final String hostname) {
    TigerConfigurationKeys.REQUEST_FILTER_HOST.putValue(
        hostname, ConfigurationValuePrecedence.TEST_CONTEXT);
  }

  /**
   * filter all subsequent findRequest steps for method.
   *
   * @param method method to filter for
   */
  @Wenn("TGR filtere Anfragen nach HTTP Methode {tigerResolvedString}")
  @When("TGR filter requests based on method {tigerResolvedString}")
  public void tgrFilterBasedOnMethod(final String method) {
    TigerConfigurationKeys.REQUEST_FILTER_METHOD.putValue(
        method.toUpperCase(), ConfigurationValuePrecedence.TEST_CONTEXT);
  }

  /** reset filter for method for subsequent findRequest steps. */
  @Wenn("TGR lösche den gesetzten HTTP Methodenfilter")
  @When("TGR reset request method filter")
  public void tgrResetRequestMethodFilter() {
    TigerGlobalConfiguration.deleteFromAllSources(
        TigerConfigurationKeys.REQUEST_FILTER_METHOD.getKey());
  }

  /**
   * Wait until a message is found in which the given node, specified by a RbelPath-Expression,
   * matches the given value. This method will NOT alter currentRequest/currentResponse!!
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn(
      "TGR warte auf eine Nachricht, in der Knoten {tigerResolvedString} mit {tigerResolvedString}"
          + " übereinstimmt")
  @When("TGR wait for message with node {tigerResolvedString} matching {tigerResolvedString}")
  public void waitForMessageWithValue(final String rbelPath, final String value) {
    rbelMessageRetriever.waitForMessageToBePresent(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireRequestMessage(false)
            .build());
  }

  /**
   * Wait until a NEW message is found in which the given node, specified by a RbelPath-Expression,
   * matches the given value. NEW in this context means that the step will wait and check only
   * messages which are received after the step has started. Any previously received messages will
   * NOT be checked. Please also note that the currentRequest/currentResponse used by the find /
   * find next steps are not altered by this step.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn(
      "TGR warte auf eine neue Nachricht, in der Knoten {tigerResolvedString} mit"
          + " {tigerResolvedString} übereinstimmt")
  @When("TGR wait for new message with node {tigerResolvedString} matching {tigerResolvedString}")
  public void waitForNewMessageWithValue(final String rbelPath, final String value) {
    rbelMessageRetriever.waitForMessageToBePresent(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireNewMessage(true)
            .requireRequestMessage(false)
            .build());
  }

  /**
   * find the first request where the path equals or matches as regex and memorize it in the {@link
   * #rbelValidator} instance
   *
   * @param path path to match
   */
  @Wenn("TGR finde die erste Anfrage mit Pfad {string}")
  @When("TGR find first request to path {string}")
  public void findRequestToPath(final String path) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).build().resolvePlaceholders());
  }

  /** DEPRECATED please use "TGR find first request to path {string}" instead. */
  @Deprecated(since = "3.7.0", forRemoval = true)
  @When("TGR find request to path {string}")
  public void findRequestToPathDeprecated(final String path) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).build().resolvePlaceholders());
  }

  /**
   * DEPRECATED please use "TGR find first request to path {string} with {string} matching {string}"
   * instead. Please use "TGR find first request to path {string} with {string} matching {string}"
   * instead.
   *
   * @deprecated
   */
  @Deprecated(since = "3.7.0", forRemoval = true)
  @When("TGR find request to path {string} with {string} matching {string}")
  public void findRequestToPathWithCommand_Deprecated(
      final String path, final String rbelPath, final String value) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the first request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelMessageRetriever} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn(
      "TGR finde die erste Anfrage mit Pfad {string} und Knoten {string} der mit {string}"
          + " übereinstimmt")
  @When("TGR find first request to path {string} with {string} matching {string}")
  public void findRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the NEXT request where the path equals or matches as regex and memorize it in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param path path to match
   */
  @Wenn("TGR finde die nächste Anfrage mit dem Pfad {string}")
  @When("TGR find next request to path {string}")
  public void findNextRequestToPath(final String path) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .startFromLastMessage(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the NEXT request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelMessageRetriever} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn(
      "TGR finde die nächste Anfrage mit Pfad {string} und Knoten {string} der mit {string}"
          + " übereinstimmt")
  @When("TGR find next request to path {string} with {string} matching {string}")
  public void findNextRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .startFromLastMessage(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the first request where path matches and request contains node with given rbel path and
   * memorize it in the {@link #rbelMessageRetriever} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die erste Anfrage mit Pfad {string} die den Knoten {string} enthält")
  @When("TGR find first request to path {string} containing node {string}")
  public void findFirstRequestToPathContainingNode(final String path, final String rbelPath) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).rbelPath(rbelPath).build().resolvePlaceholders());
  }

  /**
   * DEPRECATED please use "TGR find first request to path {string} with {string} containing node
   * {string}" instead.
   */
  @Deprecated(since = "3.7.0", forRemoval = true)
  @When("TGR find request to path {string} containing node {string}")
  public void findFirstRequestToPathContainingNodeDeprecated(
      final String path, final String rbelPath) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).rbelPath(rbelPath).build().resolvePlaceholders());
  }

  /**
   * find the NEXT request where path matches and request contains node with given rbel path and
   * memorize it in the {@link #rbelMessageRetriever} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die nächste Anfrage mit Pfad {string} die den Knoten {string} enthält")
  @When("TGR find next request to path {string} containing node {string}")
  public void findNextRequestToPathContainingNode(final String path, final String rbelPath) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .startFromLastMessage(true)
            .path(path)
            .rbelPath(rbelPath)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the LAST request where the path equals or matches as regex and memorize it in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param path path to match
   */
  @Wenn("TGR finde die letzte Anfrage mit dem Pfad {string}")
  @When("TGR find last request to path {string}")
  public void findLastRequestToPath(final String path) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the LAST request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelMessageRetriever} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn(
      "TGR finde die letzte Anfrage mit Pfad {string} und Knoten {string} der mit {string}"
          + " übereinstimmt")
  @When("TGR find last request to path {string} with {string} matching {string}")
  public void findLastRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the LAST request where the node value equal or match as regex and memorize it in the
   * {@link #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die letzte Anfrage mit Knoten {string} der mit {string}" + " übereinstimmt")
  @When("TGR find last request with {string} matching {string}")
  public void findLastRequestWithNodeMatching(final String rbelPath, final String value) {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .path(".*")
            .value(value)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /** find the LAST request. */
  @Wenn("TGR finde die letzte Anfrage")
  @When("TGR find the last request")
  public void findLastRequest() {
    rbelMessageRetriever.findLastRequest();
  }

  /**
   * assert that there is any message with given rbel path node/attribute matching given value. The
   * matching will NOT perform regular expression matching but only checks for identical string
   * content The result (request or response) will not be stored in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   * @deprecated
   */
  @Wenn(
      "TGR finde eine Nachricht mit Knoten {tigerResolvedString} der mit {tigerResolvedString}"
          + " übereinstimmt")
  @When("TGR any message with attribute {tigerResolvedString} matches {tigerResolvedString}")
  @Deprecated(forRemoval = true)
  public void findAnyMessageAttributeMatches(final String rbelPath, final String value) {
    rbelMessageRetriever.findAnyMessageMatchingAtNode(rbelPath, value);
  }

  /**
   * find the first message where node value equal or match as regex and memorize it in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die erste Nachricht mit Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find message with {string} matching {string}")
  public void findMessageWithNodeMatching(final String rbelPath, final String value)
      throws AssertionError {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireRequestMessage(false)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the first message containing given node and memorize it in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die erste Nachricht mit Knoten {string}")
  @When("TGR find message with {string}")
  public void findMessageWithNode(final String rbelPath) throws AssertionError {
    findMessageWithNodeMatching(rbelPath, null);
  }

  /**
   * find the next message where node value equal or match as regex and memorize it in the {@link
   * #rbelMessageRetriever} instance. If the previous search using the 'find*message' steps found a
   * response message, then this search starts after that response, otherwise, it starts after the
   * current request message.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die nächste Nachricht mit Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find next message with {string} matching {string}")
  public void findNextMessageWithNodeMatching(final String rbelPath, final String value)
      throws AssertionError {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireRequestMessage(false)
            .startFromLastMessage(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the next message containing given node and memorize it in the {@link
   * #rbelMessageRetriever} instance. If the previous search using any of the 'find*message' steps
   * found a response message, then this search starts after that response, otherwise, it starts
   * after the current request message.
   *
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die nächste Nachricht mit Knoten {string}")
  @When("TGR find next message with {string}")
  public void findNextMessageWithNode(final String rbelPath) throws AssertionError {
    findNextMessageWithNodeMatching(rbelPath, null);
  }

  /**
   * find the last message where node value equal or match as regex and memorize it in the {@link
   * #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die letzte Nachricht mit Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find last message with {string} matching {string}")
  public void findLastMessageWithNodeMatching(final String rbelPath, final String value)
      throws AssertionError {
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireRequestMessage(false)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the last message containing given node and memorize it in the {@link *
   * #rbelMessageRetriever} instance.
   *
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die letzte Nachricht mit Knoten {string}")
  @When("TGR find last message with {string}")
  public void findLastMessageWithNode(final String rbelPath) throws AssertionError {
    findLastMessageWithNodeMatching(rbelPath, null);
  }

  // =================================================================================================================
  //
  //    R E Q U E S T  V A L I D A T I O N
  //
  // =================================================================================================================

  /**
   * assert that request body matches.
   *
   * @param docString value / regex that should equal or match
   */
  @Dann("TGR prüfe aktueller Request stimmt im Body überein mit:")
  @Then("TGR current request body matches:")
  @ResolvableArgument
  public void currentRequestBodyMatches(final String docString) {
    currentRequestMessageAttributeMatches(
        "$.body", TigerParameterTypeDefinitions.tigerResolvedString(docString));
  }

  /**
   * assert that request matches the value at given rbel path node/attribute. If multiple nodes are
   * found, each is tested and if any matches, this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option
   */
  @Dann(
      "TGR prüfe aktueller Request stimmt im Knoten {tigerResolvedString} überein mit"
          + " {tigerResolvedString}")
  @Then("TGR current request with attribute {tigerResolvedString} matches {tigerResolvedString}")
  public void currentRequestMessageAttributeMatches(final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentRequestMatches(
        rbelPath, value, true, rbelMessageRetriever);
  }

  /**
   * assert that request contains at least 1 (or maybe more) node/attribute at given rbel path.
   *
   * @param rbelPath path to node/attribute
   */
  @Dann("TGR prüfe aktueller Request enthält Knoten {tigerResolvedString}")
  @Then("TGR current request contains node {tigerResolvedString}")
  public void currentRequestMessageContainsNode(final String rbelPath) {
    assertThat(rbelMessageRetriever.findElementsInCurrentRequest(rbelPath)).isNotEmpty();
  }

  /**
   * assert that request matches at given rbel path node/attribute. If multiple nodes are found,
   * each is tested and if any matches, this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param docString value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option supplied as DocString
   */
  @Dann("TGR prüfe aktueller Request im Knoten {tigerResolvedString} stimmt überein mit:")
  @Then("TGR current request at {tigerResolvedString} matches:")
  @ResolvableArgument
  public void currentRequestMessageAtMatchesDocString(
      final String rbelPath, final String docString) {
    currentRequestMessageAttributeMatches(
        rbelPath, TigerParameterTypeDefinitions.tigerResolvedString(docString));
  }

  /**
   * assert that request matches at given rbel path node/attribute assuming it's JSON or XML. If
   * multiple nodes are found, each is tested, and if any matches, this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param mode one of JSON|XML
   * @param oracleDocStr value / regex that should equal or match as JSON or XML content
   * @see JsonChecker#compareJsonStrings(String, String, boolean)
   */
  @Dann(
      "TGR prüfe aktueller Request im Knoten {tigerResolvedString} stimmt als {modeType} überein"
          + " mit:")
  @Then("TGR current request at {tigerResolvedString} matches as {modeType}:")
  @ResolvableArgument
  public void currentRequestAtMatchesAsJsonOrXml(
      final String rbelPath, final ModeType mode, final String oracleDocStr) {
    rbelValidator.assertAttributeOfCurrentRequestMatchesAs(
        rbelPath,
        mode,
        TigerGlobalConfiguration.resolvePlaceholders(oracleDocStr),
        rbelMessageRetriever);
  }

  /**
   * assert that request does not match at given rbel path node/attribute. If multiple nodes are
   * found, each is tested, and if any matches, this step fails.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should NOT BE equal or should NOT match as string content with
   *     MultiLine and DotAll regex option
   */
  @Dann(
      "TGR prüfe aktueller Request stimmt im Knoten {tigerResolvedString} nicht überein mit"
          + " {tigerResolvedString}")
  @Then(
      "TGR current request with attribute {tigerResolvedString} does not match"
          + " {tigerResolvedString}")
  public void currentRequestMessageAttributeDoesNotMatch(
      final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentRequestMatches(
        rbelPath, value, false, rbelMessageRetriever);
  }

  // =================================================================================================================
  //
  //    S T O R E   R E S P O N S E  / R E Q U E S T   N O D E   I N   C O N T E X T
  //
  // =================================================================================================================

  /**
   * store given rbel path node/attribute text value of current request.
   *
   * @param rbelPath path to node/attribute
   * @param varName name of variable to store the node text value in
   */
  @Dann(
      "TGR speichere Wert des Knotens {tigerResolvedString} der aktuellen Anfrage in der Variable"
          + " {tigerResolvedString}")
  @Then(
      "TGR store current request node text value at {tigerResolvedString} in variable"
          + " {tigerResolvedString}")
  public void storeCurrentRequestNodeTextValueInVariable(
      final String rbelPath, final String varName) {
    final String text =
        rbelMessageRetriever.findElementsInCurrentRequest(rbelPath).stream()
            .map(RbelElement::getRawStringContent)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.joining());
    TigerGlobalConfiguration.putValue(varName, text, ConfigurationValuePrecedence.TEST_CONTEXT);
    log.info(String.format("Storing '%s' in variable '%s'", text, varName));
  }

  /**
   * store given rbel path node/attribute text value of current response.
   *
   * @param rbelPath path to node/attribute
   * @param varName name of variable to store the node text value in
   */
  @Dann(
      "TGR speichere Wert des Knotens {tigerResolvedString} der aktuellen Antwort in der Variable"
          + " {tigerResolvedString}")
  @Then(
      "TGR store current response node text value at {tigerResolvedString} in variable"
          + " {tigerResolvedString}")
  public void storeCurrentResponseNodeTextValueInVariable(
      final String rbelPath, final String varName) {
    final String text =
        rbelMessageRetriever.findElementsInCurrentResponse(rbelPath).stream()
            .map(RbelElement::getRawStringContent)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.joining());
    TigerGlobalConfiguration.putValue(varName, text, ConfigurationValuePrecedence.TEST_CONTEXT);
    log.info(String.format("Storing '%s' in variable '%s'", text, varName));
  }

  // =================================================================================================================
  //
  //    M O D I F Y   S T O R E D   C O N T E N T
  //
  // =================================================================================================================

  /**
   * replace stored content with given regex
   *
   * @param regexPattern regular expression to search for
   * @param replace string to replace all matches with
   * @param varName name of variable to store the node text value in
   */
  @Dann(
      "TGR ersetze {tigerResolvedString} mit {tigerResolvedString} im Inhalt der Variable"
          + " {tigerResolvedString}")
  @Then(
      "TGR replace {tigerResolvedString} with {tigerResolvedString} in content of variable"
          + " {tigerResolvedString}")
  public void replaceContentOfVariable(
      final String regexPattern, final String replace, final String varName) {
    String newContent =
        TigerGlobalConfiguration.readStringOptional(varName)
            .orElseThrow(
                () ->
                    new TigerLibraryException("No configuration property '" + varName + "' found!"))
            .replaceAll(regexPattern, replace);
    TigerGlobalConfiguration.putValue(
        varName, newContent, ConfigurationValuePrecedence.TEST_CONTEXT);
    log.info(String.format("Modified content in variable '%s' to '%s'", varName, newContent));
  }

  // =================================================================================================================
  //
  //    R E S P O N S E   V A L I D A T I O N
  //
  // =================================================================================================================

  /**
   * assert that response body of filtered request matches.
   *
   * @param docString value / regex that should equal or match
   */
  @Dann("TGR prüfe aktuelle Antwort stimmt im Body überein mit:")
  @Then("TGR current response body matches:")
  @ResolvableArgument
  public void currentResponseBodyMatches(final String docString) {
    currentResponseMessageAttributeMatches(
        "$.body", TigerParameterTypeDefinitions.tigerResolvedString(docString));
  }

  /**
   * assert that response of filtered request contains at least one node/attribute at given rbel
   * path.
   *
   * @param rbelPath path to node/attribute
   */
  @Dann("TGR prüfe aktuelle Antwort enthält Knoten {tigerResolvedString}")
  @Then("TGR current response contains node {tigerResolvedString}")
  public void currentResponseMessageContainsNode(final String rbelPath) {
    assertThat(rbelMessageRetriever.findElementsInCurrentResponse(rbelPath)).isNotEmpty();
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute. If multiple
   * nodes are found, each is tested and if any matches, this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option
   */
  @Dann(
      "TGR prüfe aktuelle Antwort stimmt im Knoten {tigerResolvedString} überein mit"
          + " {tigerResolvedString}")
  @Then("TGR current response with attribute {tigerResolvedString} matches {tigerResolvedString}")
  public void currentResponseMessageAttributeMatches(final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        rbelPath, value, true, rbelMessageRetriever);
  }

  /**
   * assert that response of filtered request does not match at given rbel path node/attribute. If
   * multiple nodes are found, each is tested and if any matches, this step fails.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should NOT BE equal or should NOT match as string content with
   *     MultiLine and DotAll regex option
   */
  @Dann(
      "TGR prüfe aktuelle Antwort stimmt im Knoten {tigerResolvedString} nicht überein mit"
          + " {tigerResolvedString}")
  @Then(
      "TGR current response with attribute {tigerResolvedString} does not match"
          + " {tigerResolvedString}")
  public void currentResponseMessageAttributeDoesNotMatch(
      final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        rbelPath, value, false, rbelMessageRetriever);
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute. If multiple
   * nodes are found, each is tested and if any matches, this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param docString value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option supplied as DocString
   */
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt überein mit:")
  @Then("TGR current response at {tigerResolvedString} matches:")
  @ResolvableArgument
  public void currentResponseMessageAtMatchesDocString(
      final String rbelPath, final String docString) {
    currentResponseMessageAttributeMatches(
        rbelPath, TigerParameterTypeDefinitions.tigerResolvedString(docString));
  }

  /**
   * assert that response of filtered request does not match at given rbel path node/attribute. If
   * multiple nodes are found, each is tested and if any matches, this step fails.
   *
   * @param rbelPath path to node/attribute
   * @param docString value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option supplied as DocString
   */
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt nicht überein mit:")
  @Then("TGR current response at {tigerResolvedString} does not match:")
  @ResolvableArgument
  public void currentResponseMessageAtDoesNotMatchDocString(
      final String rbelPath, final String docString) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        rbelPath,
        TigerGlobalConfiguration.resolvePlaceholders(docString),
        false,
        rbelMessageRetriever);
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute assuming
   * it's JSON or XML. If multiple nodes match, each is tested, and if any match this step succeeds.
   *
   * @param rbelPath path to node/attribute
   * @param mode one of JSON|XML
   * @param oracleDocStr value / regex that should equal or match as JSON or XML content
   * @see JsonChecker#compareJsonStrings(String, String, boolean)
   */
  @Dann(
      "TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt als {modeType} überein"
          + " mit:")
  @Then("TGR current response at {tigerResolvedString} matches as {modeType}:")
  @ResolvableArgument
  public void currentResponseAtMatchesAsJsonOrXml(
      final String rbelPath, final ModeType mode, final String oracleDocStr) {
    rbelValidator.assertAttributeOfCurrentResponseMatchesAs(
        rbelPath,
        mode,
        TigerGlobalConfiguration.resolvePlaceholders(oracleDocStr),
        "",
        rbelMessageRetriever);
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute assuming its
   * XML with the given list of diff options. If multiple nodes match, each is tested and if any
   * match, this step succeeds
   *
   * @param rbelPath path to node/attribute
   * @param diffOptionsCSV a csv separated list of diff option identifiers to be applied to
   *     comparison of the two XML sources
   *     <ul>
   *       <li>nocomment ... {@link DiffBuilder#ignoreComments()}
   *       <li>txtignoreempty ... {@link DiffBuilder#ignoreElementContentWhitespace()}
   *       <li>txttrim ... {@link DiffBuilder#ignoreWhitespace()}
   *       <li>txtnormalize ... {@link DiffBuilder#normalizeWhitespace()}
   *     </ul>
   *
   * @param xmlDocStr value / regex that should equal or match as JSON content
   * @see <a href="https://github.com/xmlunit/user-guide/wiki/DifferenceEvaluator">More on
   *     DifferenceEvaluator</a>
   */
  @Dann(
      "TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt als XML mit folgenden diff"
          + " Optionen {tigerResolvedString} überein mit:")
  @Then(
      "TGR current response at {tigerResolvedString} matches as XML and diff options"
          + " {tigerResolvedString}:")
  @ResolvableArgument
  public void currentResponseAtMatchesAsXMLAndDiffOptions(
      final String rbelPath, String diffOptionsCSV, final String xmlDocStr) {
    rbelValidator.assertAttributeOfCurrentResponseMatchesAs(
        rbelPath,
        ModeType.XML,
        TigerGlobalConfiguration.resolvePlaceholders(xmlDocStr),
        diffOptionsCSV,
        rbelMessageRetriever);
  }

  /** Prints the rbel-tree of all requests and responses to the System-out */
  @Dann("TGR gebe alle Nachrichten als Rbel-Tree aus")
  @Then("TGR print all messages as rbel-tree")
  @SuppressWarnings("java:S106")
  public void printAllMessages() {
    getRbelMessageRetriever()
        .getRbelMessages()
        .forEach(
            message ->
                System.out.println(
                    StringUtils.repeat('=', 80) + "\n" + message.printTreeStructure()));
  }

  /** Prints the rbel-tree of the current response to the System-out */
  @Dann("TGR gebe aktuelle Response als Rbel-Tree aus")
  @Then("TGR print current response as rbel-tree")
  @SuppressWarnings("java:S106")
  public void printCurrentResponse() {
    System.out.println(rbelMessageRetriever.getCurrentResponse().printTreeStructure());
  }

  /** Prints the rbel-tree of the current request to the System-out */
  @Dann("TGR gebe aktuelle Request als Rbel-Tree aus")
  @Then("TGR print current request as rbel-tree")
  @SuppressWarnings("java:S106")
  public void printCurrentRequest() {
    System.out.println(rbelMessageRetriever.getCurrentRequest().printTreeStructure());
  }

  /** Reads a Tiger traffic file and sends messages to local Tiger proxy */
  @Dann("TGR liest folgende .tgr Datei {tigerResolvedString}")
  @Then("TGR reads the following .tgr file {tigerResolvedString}")
  public void readTgrFile(String filePath) {
    TigerProxy tigerProxy = rbelMessageRetriever.getTigerTestEnvMgr().getLocalTigerProxyOrFail();
    List<RbelElement> readElements = tigerProxy.readTrafficFromTgrFile(filePath);
    readElements.forEach(
        e -> rbelMessageRetriever.getLocalProxyRbelMessageListener().triggerNewReceivedMessage(e));
  }

  /**
   * Sets a custom failure message that will be displayed in the logs if a following step in the
   * test fails.
   *
   * @param customFailureMessage the custom failure message
   */
  @Given("TGR the custom failure message is set to {tigerResolvedString}")
  @Gegebensei("TGR die Fehlermeldung wird gesetzt auf: {tigerResolvedString}")
  public void setCustomFailureMessage(String customFailureMessage) {
    TigerConfigurationKeys.CUSTOM_FAILURE_MESSAGE.putValue(customFailureMessage);
  }

  /** Clears the custom failure message */
  @Then("TGR clear the custom failure message")
  @Dann("TGR lösche die benutzerdefinierte Fehlermeldung")
  public void resetCustomFailureMessage() {
    TigerConfigurationKeys.CUSTOM_FAILURE_MESSAGE.clearValue();
  }

  private static RbelConverter getRbelConverter() {
    return TigerDirector.getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .orElseThrow(() -> new TigerTestEnvException("No local tiger proxy configured"))
        .getRbelLogger()
        .getRbelConverter();
  }

  /**
   * Deactivate the rbel parsing for the given parsers.
   *
   * @param parsersToDeactivate a comma separated list of parser identifiers to deactivate.
   */
  @Given("TGR the rbel parsing is deactivated for {string}")
  @Gegebensei("TGR das rbel parsing ist inaktiv für {string}")
  @Gegebensei("TGR die Parser für {string} sind deaktiviert")
  public void deactivateParsingFor(String parsersToDeactivate) {
    getRbelConverter().deactivateParsingFor(parsersToDeactivate);
  }

  /**
   * Activate the rbel parsing for the given parsers.
   *
   * @param parsersToActivate a comma separated list of parser identifiers to activate.
   */
  @Given("TGR the rbel parsing is activated for {string}")
  @Gegebensei("TGR die Parser für {string} sind aktiviert")
  public void activateParsingFor(String parsersToActivate) {
    getRbelConverter().activateParsingFor(parsersToActivate);
  }

  /** Activate all parsers that were configured at startup */
  @Given("TGR the rbel parsing is activated for all configured parsers")
  @Gegebensei("TGR alle konfigurierten Parser sind aktiviert")
  public void activateParsingForAll() {
    getRbelConverter().activateParsingForAll();
  }

  @Deprecated(forRemoval = true)
  @Given("TGR the rbel parsing is reactivated for all configured parsers")
  @Gegebensei("TGR das rbel parsing ist wieder aktiv für alle konfigurierten Parser")
  public void reactivateParsingForAll() {
    getRbelConverter().reactivateParsingForAll();
  }

  /** Deactivate the rbel parsing for all optional parsers. */
  @Given("TGR all optional rbel parsers are deactivated")
  @Gegebensei("TGR alle optionalen Parser sind deaktiviert")
  public void deactivateOptionalParsing() {
    getRbelConverter().deactivateOptionalParsing();
  }
}
