/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.lib.enums.ModeType;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.rbel.RbelMessageValidator;
import de.gematik.test.tiger.lib.rbel.RequestParameter;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.xmlunit.builder.DiffBuilder;

@Slf4j
public class RBelValidatorGlue {

  static final RbelMessageValidator rbelValidator = RbelMessageValidator.instance;

  public static RbelMessageValidator getRbelValidator() {
    return rbelValidator;
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
        "tiger.rbel.request.timeout", waitsec, SourceType.TEST_CONTEXT);
  }

  /**
   * clear all validatable rbel messages. This does not clear the recorded messages later on
   * reported via the rbel log HTML page or the messages shown on web ui of tiger proxies.
   */
  @Wenn("TGR lösche aufgezeichnete Nachrichten")
  @When("TGR clear recorded messages")
  public void tgrClearRecordedMessages() {
    rbelValidator.clearRbelMessages();
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
    TigerGlobalConfiguration.putValue(
        "tiger.rbel.request.filter.host", hostname, SourceType.TEST_CONTEXT);
  }

  /**
   * filter all subsequent findRequest steps for method.
   *
   * @param method method to filter for
   */
  @Wenn("TGR filtere Anfragen nach HTTP Methode {tigerResolvedString}")
  @When("TGR filter requests based on method {tigerResolvedString}")
  public void tgrFilterBasedOnMethod(final String method) {
    TigerGlobalConfiguration.putValue(
        "tiger.rbel.request.filter.method", method.toUpperCase(), SourceType.TEST_CONTEXT);
  }

  /** reset filter for method for subsequent findRequest steps. */
  @Wenn("TGR lösche den gesetzten HTTP Methodenfilter")
  @When("TGR reset request method filter")
  public void tgrResetRequestMethodFilter() {
    TigerGlobalConfiguration.putValue(
        "tiger.rbel.request.filter.method", null, SourceType.TEST_CONTEXT);
  }

  /**
   * Wait until a message is found in which the given node, specified by a RbelPath-Expression,
   * matches the given value. This method will NOT alter currentRequest/currentResponse!!
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR warte auf eine Nachricht, in der Knoten {tigerResolvedString} mit {tigerResolvedString} übereinstimmt")
  @When("TGR wait for message with node {tigerResolvedString} matching {tigerResolvedString}")
  public void waitForMessageWithValue(final String rbelPath, final String value) {
    rbelValidator.waitForMessageToBePresent(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireHttpMessage(false)
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
  @Wenn("TGR warte auf eine neue Nachricht, in der Knoten {tigerResolvedString} mit {tigerResolvedString} übereinstimmt")
  @When("TGR wait for new message with node {tigerResolvedString} matching {tigerResolvedString}")
  public void waitForNewMessageWithValue(final String rbelPath, final String value) {
    rbelValidator.waitForMessageToBePresent(
        RequestParameter.builder()
            .rbelPath(rbelPath)
            .value(value)
            .requireNewMessage(true)
            .requireHttpMessage(false)
            .build());
  }

  /**
   * find the first request where the path equals or matches as regex and memorize it in the {@link
   * #rbelValidator} instance
   *
   * @param path path to match
   */
  @Wenn("TGR finde die erste Anfrage mit Pfad {string}")
  @When("TGR find request to path {string}")
  public void findRequestToPath(final String path) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).build().resolvePlaceholders());
  }

  /**
   * find the first request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelValidator} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die erste Anfrage mit Pfad {string} und Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find request to path {string} with {string} matching {string}")
  public void findRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the NEXT request where the path equals or matches as regex and memorize it in the {@link
   * #rbelValidator} instance.
   *
   * @param path path to match
   */
  @Wenn("TGR finde die nächste Anfrage mit dem Pfad {string}")
  @When("TGR find next request to path {string}")
  public void findNextRequestToPath(final String path) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .startFromLastRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the NEXT request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelValidator} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die nächste Anfrage mit Pfad {string} und Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find next request to path {string} with {string} matching {string}")
  public void findNextRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .startFromLastRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the first request where path matches and request contains node with given rbel path and
   * memorize it in the {@link #rbelValidator} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die erste Anfrage mit Pfad {string} die den Knoten {string} enthält")
  @When("TGR find request to path {string} containing node {string}")
  public void findFirstRequestToPathContainingNode(final String path, final String rbelPath) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).rbelPath(rbelPath).build().resolvePlaceholders());
  }

  /**
   * find the NEXT request where path matches and request contains node with given rbel path and
   * memorize it in the {@link #rbelValidator} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   */
  @Wenn("TGR finde die nächste Anfrage mit Pfad {string} die den Knoten {string} enthält")
  @When("TGR find next request to path {string} containing node {string}")
  public void findNextRequestToPathContainingNode(final String path, final String rbelPath) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(path).rbelPath(rbelPath).build().resolvePlaceholders());
  }

  /**
   * find the LAST request where the path equals or matches as regex and memorize it in the {@link
   * #rbelValidator} instance.
   *
   * @param path path to match
   */
  @Wenn("TGR finde die letzte Anfrage mit dem Pfad {string}")
  @When("TGR find last request to path {string}")
  public void findLastRequestToPath(final String path) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /**
   * find the LAST request where path and node value equal or match as regex and memorize it in the
   * {@link #rbelValidator} instance.
   *
   * @param path path to match
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   */
  @Wenn("TGR finde die letzte Anfrage mit Pfad {string} und Knoten {string} der mit {string} übereinstimmt")
  @When("TGR find last request to path {string} with {string} matching {string}")
  public void findLastRequestToPathWithCommand(
      final String path, final String rbelPath, final String value) {
    rbelValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(path)
            .rbelPath(rbelPath)
            .value(value)
            .filterPreviousRequest(true)
            .build()
            .resolvePlaceholders());
  }

  /** find the LAST request. */
  @Wenn("TGR finde die letzte Anfrage")
  @When("TGR find the last request")
  public void findLastRequest() {
    rbelValidator.findLastRequest();
  }

  /**
   * assert that there is any message with given rbel path node/attribute matching given value. The
   * matching will NOT perform regular expression matching but only checks for identical string
   * content The result (request or response) will not be stored in the {@link #rbelValidator}
   * instance.
   *
   * @param rbelPath rbel path to node/attribute
   * @param value value to match at given node/attribute
   * @deprecated
   */
  @Wenn("TGR finde eine Nachricht mit Knoten {tigerResolvedString} der mit {tigerResolvedString} übereinstimmt")
  @When("TGR any message with attribute {tigerResolvedString} matches {tigerResolvedString}")
  @Deprecated(forRemoval = true)
  public void findAnyMessageAttributeMatches(final String rbelPath, final String value) {
    rbelValidator.findAnyMessageMatchingAtNode(rbelPath, value);
  }

  // =================================================================================================================
  //
  //    S T O R E   R E S P O N S E   N O D E   I N   C O N T E X T
  //
  // =================================================================================================================

  /**
   * store given rbel path node/attribute text value of current response.
   *
   * @param rbelPath path to node/attribute
   * @param varName name of variable to store the node text value in
   */
  @Dann("TGR speichere Wert des Knotens {tigerResolvedString} der aktuellen Antwort in der Variable {tigerResolvedString}")
  @Then("TGR store current response node text value at {tigerResolvedString} in variable {tigerResolvedString}")
  public void storeCurrentResponseNodeTextValueInVariable(
      final String rbelPath, final String varName) {
    final String text =
        rbelValidator.findElementsInCurrentResponse(rbelPath).stream()
            .map(RbelElement::getRawStringContent)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.joining());
    TigerGlobalConfiguration.putValue(varName, text, SourceType.TEST_CONTEXT);
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
  @Dann("TGR ersetze {tigerResolvedString} mit {tigerResolvedString} im Inhalt der Variable {tigerResolvedString}")
  @Then("TGR replace {tigerResolvedString} with {tigerResolvedString} in content of variable {tigerResolvedString}")
  public void replaceContentOfVariable(
      final String regexPattern, final String replace, final String varName) {
    String newContent =
        TigerGlobalConfiguration.readStringOptional(varName)
            .orElseThrow(
                () ->
                    new TigerLibraryException("No configuration property '" + varName + "' found!"))
            .replaceAll(regexPattern, replace);
    TigerGlobalConfiguration.putValue(varName, newContent, SourceType.TEST_CONTEXT);
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
  public void currentResponseBodyMatches(final String docString) {
    currentResponseMessageAttributeMatches(
        "$.body", TigerParameterTypeDefinitions.tigerResolvedString(docString));
  }

  /**
   * assert that response of filtered request contains node/attribute at given rbel path.
   *
   * @param rbelPath path to node/attribute
   */
  @Dann("TGR prüfe aktuelle Antwort enthält Knoten {tigerResolvedString}")
  @Then("TGR current response contains node {tigerResolvedString}")
  public void currentResponseMessageContainsNode(final String rbelPath) {
    assertThat(rbelValidator.findElementsInCurrentResponse(rbelPath)).isNotEmpty();
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option
   */
  @Dann("TGR prüfe aktuelle Antwort stimmt im Knoten {tigerResolvedString} überein mit {tigerResolvedString}")
  @Then("TGR current response with attribute {tigerResolvedString} matches {tigerResolvedString}")
  public void currentResponseMessageAttributeMatches(final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(rbelPath, value, true);
  }

  /**
   * assert that response of filtered request does not match at given rbel path node/attribute.
   *
   * @param rbelPath path to node/attribute
   * @param value value / regex that should NOT BE equal or should NOT match as string content with
   *     MultiLine and DotAll regex option
   */
  @Dann("TGR prüfe aktuelle Antwort stimmt im Knoten {tigerResolvedString} nicht überein mit {tigerResolvedString}")
  @Then("TGR current response with attribute {tigerResolvedString} does not match {tigerResolvedString}")
  public void currentResponseMessageAttributeDoesNotMatch(
      final String rbelPath, final String value) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(rbelPath, value, false);
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute.
   *
   * @param rbelPath path to node/attribute
   * @param docString value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option supplied as DocString
   */
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt überein mit:")
  @Then("TGR current response at {tigerResolvedString} matches:")
  public void currentResponseMessageAtMatchesDocString(
      final String rbelPath, final String docString) {
    currentResponseMessageAttributeMatches(rbelPath, docString);
  }

  /**
   * assert that response of filtered request does not match at given rbel path node/attribute.
   *
   * @param rbelPath path to node/attribute
   * @param docString value / regex that should equal or match as string content with MultiLine and
   *     DotAll regex option supplied as DocString
   */
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt nicht überein mit:")
  @Then("TGR current response at {tigerResolvedString} does not match:")
  public void currentResponseMessageAtDoesNotMatchDocString(
      final String rbelPath, final String docString) {
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        rbelPath, TigerGlobalConfiguration.resolvePlaceholders(docString), false);
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute assuming its
   * JSON or XML
   *
   * @param rbelPath path to node/attribute
   * @param mode one of JSON|XML
   * @param oracleDocStr value / regex that should equal or match as JSON or XML content
   * @see JsonChecker#compareJsonStrings(String, String, boolean)
   */
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt als {modeType} überein mit:")
  @Then("TGR current response at {tigerResolvedString} matches as {modeType}:")
  public void currentResponseAtMatchesAsJsonOrXml(
      final String rbelPath, final ModeType mode, final String oracleDocStr) {
    rbelValidator.assertAttributeOfCurrentResponseMatchesAs(
        rbelPath, mode, TigerGlobalConfiguration.resolvePlaceholders(oracleDocStr));
  }

  /**
   * assert that response of filtered request matches at given rbel path node/attribute assuming its
   * XML with given list of diff options.
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
  @Dann("TGR prüfe aktuelle Antwort im Knoten {tigerResolvedString} stimmt als XML mit folgenden diff Optionen {tigerResolvedString} überein mit:")
  @Then("TGR current response at {tigerResolvedString} matches as XML and diff options {tigerResolvedString}:")
  public void currentResponseAtMatchesAsXMLAndDiffOptions(
      final String rbelPath, String diffOptionsCSV, final String xmlDocStr) {
    rbelValidator.compareXMLStructureOfRbelElement(
        rbelValidator.findElementInCurrentResponse(rbelPath),
        TigerGlobalConfiguration.resolvePlaceholders(xmlDocStr),
        diffOptionsCSV);
  }

  /** Prints the rbel-tree of the current response to the System-out */
  @Dann("TGR gebe aktuelle Response als Rbel-Tree aus")
  @Then("TGR print current response as rbel-tree")
  @SuppressWarnings("java:S106")
  public void printCurrentResponse() {
    System.out.println(
        RBelValidatorGlue.getRbelValidator().getCurrentResponse().printTreeStructure());
  }

  /** Prints the rbel-tree of the current request to the System-out */
  @Dann("TGR gebe aktuelle Request als Rbel-Tree aus")
  @Then("TGR print current request as rbel-tree")
  @SuppressWarnings("java:S106")
  public void printCurrentRequest() {
    System.out.println(
        RBelValidatorGlue.getRbelValidator().getCurrentRequest().printTreeStructure());
  }

  /** Read TGR file and sends messages to local Tiger proxy */
  @Dann("TGR liest folgende .tgr Datei {tigerResolvedString}")
  @Then("TGR reads the following .tgr file {tigerResolvedString}")
  public void readTgrFile(String filePath) {
    rbelValidator.readTgrFile(filePath);
  }
}
