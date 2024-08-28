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
 *
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addTwoRequestsToTigerTestHooks;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.buildElementsFromTgrFile;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCetpFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.lib.enums.ModeType;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
class RbelMessageValidatorTest extends AbstractRbelMessageValidatorTest {

  @BeforeEach
  public void setUp() {
    super.setUp();
  }

  @AfterEach
  public void cleanUp() {
    AbstractRbelMessageValidatorTest.tearDown();
  }

  @Test
  void testPathEqualsWithRelativePath_OK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("/foo/bar?sch=mar"), "/foo/bar"))
        .isTrue();
  }

  @Test
  void testPathEqualsWithUrl_OK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "/foo/bar"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlLeading_OK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/.*\\/bar"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlTrailing_OK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/foo\\/.*"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlInMid_OK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "\\/foo\\/.*/test"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithNotMatchRegex_NOK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "/foo/.*/[test]"))
        .isFalse();
  }

  @Test
  void testPathMatchingWithInvalidRegex_NOK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "["))
        .isFalse();
  }

  @Test
  void testInvalidPathMatching_NOK() {
    assertThat(
            rbelMessageValidator.doesPathOfMessageMatch(buildRequestWithPath("file$:."), "/foo/.*"))
        .isFalse();
  }

  private RbelElement buildRequestWithPath(final String path) {
    final RbelElement rbelElement = new RbelElement(null, null);
    rbelElement.addFacet(
        RbelHttpRequestFacet.builder().path(new RbelElement(path.getBytes(), null)).build());
    return rbelElement;
  }

  @Test
  void testHostMatching_OK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "localhost"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegex_OK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "local.*"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegexNotMatching_OK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "eitzen.*"))
        .isFalse();
  }

  @Test
  void testHostMatchingInvalidRegex_NOK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "["))
        .isFalse();
  }

  @Test
  void testMethodMatching_OK() {
    assertThat(
            rbelMessageValidator.doesMethodMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "GET"))
        .isTrue();
  }

  @Test
  void testMethodMatchingNotMatching_OK() {
    assertThat(
            rbelMessageValidator.doesMethodMatch(
                buildElementsFromTgrFile("simpleHttpRequests.tgr").get(0), "POST"))
        .isFalse();
  }

  @Test
  void testSourceTestInvalid_NOK() {
    assertThatThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment-- --></body>",
                    "<root><header></header><body></body></root>"))
        .isInstanceOf(org.xmlunit.XMLUnitException.class);
  }

  @Test
  void testSourceOracleInvalid_NOK() {
    assertThatThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment --></body></root>",
                    "<root><header></header><body></body>"))
        .isInstanceOf(org.xmlunit.XMLUnitException.class);
  }

  @Test
  void testSourceNoComment_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body><!-- test comment --></body></root>",
                    "<root><header></header><body></body></root>",
                    "nocomment"));
  }

  @Test
  void testSourceNoCommetTxtTrim_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body>test     <!-- test comment --></body></root>",
                    "<root><header></header><body>test</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim2_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body>    test     <!-- test comment --></body></root>",
                    "<root><header></header><body>test</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim3_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body>    test xxx    <!-- test comment"
                        + " --></body></root>",
                    "<root><header></header><body>test xxx</body></root>",
                    "nocomment,txttrim"));
  }

  @Test
  void testSourceNoCommetTxtTrim4_OK() {
    assertThatThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body>    test   xxx    <!-- test comment"
                        + " --></body></root>",
                    "<root><header></header><body>test xxx</body></root>",
                    "nocomment,txttrim"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testSourceNoCommetTxtNormalize_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body>  test    xxxx   </body>  <!-- test comment"
                        + " --></root>",
                    "<root><header></header><body>test xxxx </body></root>",
                    "nocomment,txtnormalize"));
  }

  @Test
  void testSourceAttrOrder_OK() {
    assertThatNoException()
        .isThrownBy(
            () ->
                rbelMessageValidator.compareXMLStructure(
                    "<root><header></header><body attr1='1'   attr2='2'></body></root>",
                    "<root><header></header><body attr2='2' attr1='1'></body></root>"));
  }

  @ParameterizedTest
  @CsvSource({"http://server, ''", "http://server/, /", "http://server, /", "http://server/, ''"})
  void testEmptyPathMatching(final String url, final String path) {
    assertThat(rbelMessageValidator.doesPathOfMessageMatch(buildRequestWithPath(url), path))
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"http://server/blu/, /", "http://server/, /bla", "http://server/bla, ''"})
  void testPathOfMessageMatch_NOK(final String url, final String path) {
    assertThat(rbelMessageValidator.doesPathOfMessageMatch(buildRequestWithPath(url), path))
        .isFalse();
  }

  @Test
  void testFilterRequests_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
    RbelElement request = rbelMessageValidator.currentRequest;
    assertTrue(validator.doesHostMatch(request, "localhost"));
  }

  @Test
  void testFilterRequestsWrongPath_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    final RequestParameter requestParameter = RequestParameter.builder().path("/NOWAY.*").build();
    assertThatThrownBy(() -> validator.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFilterRequestsNextRequest_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());

    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").startFromLastMessage(true).build());
    RbelElement request = rbelMessageValidator.currentRequest;

    assertTrue(validator.doesHostMatch(request, "eitzen.at"));
  }

  @Test
  void testFindLastRequest_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.findLastRequest();
    RbelElement request = rbelMessageValidator.currentRequest;
    assertTrue(validator.doesHostMatch(request, "eitzen.at"));
  }

  @Test
  void testFilterRequestsRbelPath_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonalagent")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageValidator.currentRequest, "eitzen.at"));
  }

  @Test
  void testFilterRequestsRbelPathNotMatching_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    var reuqest =
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonalagentXXXX")
            .build();
    assertThatThrownBy(() -> rbelMessageValidator.filterRequestsAndStoreInContext(reuqest))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFilterRequestsRbelPathRegex_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonal.*")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageValidator.currentRequest, "eitzen.at"));
  }

  @Test
  void testFilterRequestsRbelPathExists_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").build());
    assertTrue(validator.doesHostMatch(rbelMessageValidator.currentRequest, "localhost"));
  }

  @Test
  void testFilterRequestsRbelPathExists2_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertTrue(validator.doesHostMatch(rbelMessageValidator.currentRequest, "eitzen.at"));
  }

  @Test
  void testFilterRequestsRbelPathExists_NOK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    var request = RequestParameter.builder().path(".*").rbelPath("$.header.User-AgentXXX").build();
    assertThatThrownBy(() -> rbelMessageValidator.filterRequestsAndStoreInContext(request))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFilterRequestsAttachResponseCorrectly_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonal.*")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageValidator.currentRequest, "eitzen.at"));
    assertThat(
            rbelMessageValidator
                .currentResponse
                .getFacetOrFail(RbelHttpResponseFacet.class)
                .getResponseCode()
                .getRawStringContent())
        .isEqualTo("500");
  }

  @Test
  void testFindElementInCurrentResponse_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    Assertions.assertThat(validator.findElementInCurrentResponse("$.body..h1"))
        .isInstanceOf(RbelElement.class);
  }

  @Test
  void testFindElementInCurrentResponse_NOK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertThatThrownBy(() -> validator.findElementInCurrentResponse("$.body..h2"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindElementInCurrentRequest_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    Assertions.assertThat(validator.findElementInCurrentRequest("$..User-Agent"))
        .isInstanceOf(RbelElement.class);
  }

  @Test
  void testFindElementInCurrentRequest_NOK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertThatThrownBy(() -> validator.findElementInCurrentRequest("$..UnknownHeader"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testValidatorAllowsToMatchNodesBeingBooleanRbelValues_True() {
    // parse in signature cert
    final RbelMessageValidator validator = addMessagePair();

    // validate
    validator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "true", true);
    validator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "false", false);
    validator.findAnyMessageMatchingAtNode("$.body.challenge.content.signature.isValid", "true");
    assertThatThrownBy(
            () ->
                validator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid", "true", false))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                validator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid", "false", true))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                validator.findAnyMessageMatchingAtNode(
                    "$.body.challenge.content.signature.isValid", "false"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testValidatorAllowsToMatchNodesBeingBooleanRbelValues_False() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    // add signed response as current response without sign cert being avail
    RbelMessageValidator validator = rbelMessageValidator;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks("getChallenge.curl", StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(
            challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    rbelMessageValidator.currentResponse = convertedMessage;
    validatableMessagesMock.add(convertedMessage);

    // validate
    validator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "false", true);
    validator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "true", false);
    validator.findAnyMessageMatchingAtNode("$.body.challenge.content.signature.isValid", "false");
    assertThatThrownBy(
            () ->
                validator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid", "false", false))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                validator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid", "true", true))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                validator.findAnyMessageMatchingAtNode(
                    "$.body.challenge.content.signature.isValid", "true"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testCurrentRequestMatchesAsExpected() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    RbelMessageValidator validator = rbelMessageValidator;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks("getCurrentRequest.curl", StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(
            challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    rbelMessageValidator.currentRequest = convertedMessage;
    validatableMessagesMock.add(convertedMessage);
    validator.findElementsInCurrentRequest("$.body.foo");
    validator.assertAttributeOfCurrentRequestMatches("$.body.foo", "bar", true);
    String oracleStr = "{'foo': '${json-unit.ignore}'}";
    validator.assertAttributeOfCurrentRequestMatchesAs("$.body", ModeType.JSON, oracleStr);

    log.info("Current Request: {}", rbelMessageValidator.currentRequest);
    log.info("converted message: {}", convertedMessage);

    glue.currentRequestBodyMatches("!{rbel:currentRequestAsString('$.body')}");
    glue.currentRequestMessageAttributeMatches("$.body.foo", "bar");
    glue.currentRequestMessageContainsNode("$.body.foo");
    glue.currentRequestMessageAtMatchesDocString("$.body", "{\"foo\":\"bar\"}\r\n");
    glue.currentRequestAtMatchesAsJsonOrXml("$.body", ModeType.JSON, oracleStr);
    glue.currentRequestMessageAttributeDoesNotMatch("$.body.foo", "foo");
  }

  @Test
  void testCurrentRequestMatchesJsonSchemaWithPlaceholdersReplacement() {
    val responseToCheck =
        """
      HTTP/1.1 200 OK

      ["hello", "world"]
      """;
    val schema =
        """
      {
       "type": "array",
        "prefixItems": [
          {
            "type": "string",
            "const": "${value.from.config1}"
          },
          {
            "type": "string",
            "const": "${value.from.config2}"
          }
        ],
        "additionalItems": false
      }
      """;
    TigerGlobalConfiguration.putValue("value.from.config1", "hello");
    TigerGlobalConfiguration.putValue("value.from.config2", "world");

    rbelMessageValidator.currentRequest =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(responseToCheck.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    glue.currentRequestAtMatchesAsJsonOrXml("$.body", ModeType.JSON_SCHEMA, schema);

    TigerGlobalConfiguration.reset();
  }

  @Test
  void testCurrentRequestDoesNotMatchAsExpected() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    RbelMessageValidator validator = rbelMessageValidator;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks("getCurrentRequest.curl", StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(
            challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    rbelMessageValidator.currentRequest = convertedMessage;
    validatableMessagesMock.add(convertedMessage);

    validator.assertAttributeOfCurrentRequestMatches("$.body.foo", "blala", false);
  }

  @Test
  void testCurrentRequestMatchesFailure() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    RbelMessageValidator validator = rbelMessageValidator;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks("getCurrentRequest.curl", StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(
            challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    rbelMessageValidator.currentRequest = convertedMessage;
    validatableMessagesMock.add(convertedMessage);
    assertThatThrownBy(
            () -> validator.assertAttributeOfCurrentRequestMatches("$.body.foo", "blabla", true))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testCurrentMessagesNotFound() {
    readTgrFileAndStoreForRbelMessageValidator(
        "src/test/resources/testdata/interleavedRequests.tgr");
    RbelMessageValidator validator = rbelMessageValidator;

    RequestParameter rootElementRequest = RequestParameter.builder().rbelPath("$").build();
    assertDoesNotThrow(() -> validator.filterRequestsAndStoreInContext(rootElementRequest));
    assertDoesNotThrow(() -> validator.findElementInCurrentRequest("$"));
    assertDoesNotThrow(() -> validator.findElementInCurrentResponse("$"));

    RequestParameter nonExistingElementRequest =
        RequestParameter.builder().rbelPath("$.x.y").build();
    assertThatThrownBy(() -> validator.filterRequestsAndStoreInContext(nonExistingElementRequest))
        .isInstanceOf(AssertionError.class);

    var assertionError =
        assertThrows(AssertionError.class, () -> validator.findElementInCurrentRequest("$"));
    assertThat(assertionError.getMessage()).isEqualTo("No current request message found!");

    var assertionError2 =
        assertThrows(AssertionError.class, () -> validator.findElementInCurrentResponse("$"));
    assertThat(assertionError2.getMessage()).isEqualTo("No current response message found!");
  }

  @Test
  void testUnpairedRequest() {
    readTgrFileAndStoreForRbelMessageValidator("src/test/resources/testdata/cetpExampleFlow.tgr");

    rbelMessageValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder().rbelPath("$.body.Event.Topic.text").build());

    assertDoesNotThrow(() -> rbelMessageValidator.findElementInCurrentRequest("$..Topic.text"));

    var assertionError =
        assertThrows(
            AssertionError.class, () -> rbelMessageValidator.findElementInCurrentResponse("$"));
    assertThat(assertionError.getMessage()).isEqualTo("No current response message found!");
  }

  /*
  testFindMultipleNodesInRequestShouldMatch_OK
  testFindMultipleNodesInRequestShouldMatch_NOK
  testFindMultipleNodesInRequestShouldNOTMatch_OK
  testFindMultipleNodesInRequestShouldNOTMatch_NOK

   TODO
   currentResponseAtMatchesAsXMLAndDiffOptions
  */

  @Test
  void testFindMultipleNodesInRequestShouldMatch_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    gluecode.currentResponseMessageAttributeMatches("$..td.a.text", "apidocs.*");
  }

  @Test
  void testFindMultipleNodesInRequestShouldMatch_NOK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    assertThatThrownBy(
            () ->
                gluecode.currentResponseMessageAttributeMatches(
                    "$..td.a.text", "SOMETHING THATNEVERMATCHES.*"))
        .isInstanceOf(AssertionError.class)
        .hasMessageFindingMatch("Expected that nodes to rbel path '.*' are equal to or match '.*'");
  }

  @Test
  void testFindMultipleNodesInRequestShouldNotMatch_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    gluecode.currentResponseMessageAttributeDoesNotMatch(
        "$..td.a.text", "SOMETHINGTHATNEVERMATCHES.*");
  }

  @Test
  void testFindMultipleNodesInRequestShouldNotMatch_NOK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    assertThatThrownBy(
            () -> gluecode.currentResponseMessageAttributeDoesNotMatch("$..td.a.text", "apidocs.*"))
        .isInstanceOf(AssertionError.class)
        .hasMessageStartingWith("Did not expect that value 'apidocs/");
  }

  @Test
  void testThatWaitForNonPairedMessageToBePresentFindsTargetMessage()
      throws ExecutionException, InterruptedException {
    final RequestParameter messageParameters =
        RequestParameter.builder().rbelPath("$..Topic.text").value("CT/CONNECTED").build();
    CompletableFuture<RbelElement> waitForMessageFuture =
        CompletableFuture.supplyAsync(
            () -> rbelMessageValidator.waitForMessageToBePresent(messageParameters));

    readTgrFileAndStoreForRbelMessageValidator("src/test/resources/testdata/cetpExampleFlow.tgr");

    waitForMessageFuture.get();
    assertThat(rbelMessageValidator.findMessageByDescription(messageParameters))
        .extractChildWithPath("$..Topic.text")
        .hasStringContentEqualTo("CT/CONNECTED");
  }

  @Test
  void testWaitingForNewNonPairedMessage() throws ExecutionException, InterruptedException {

    RbelMessageValidator.RBEL_REQUEST_TIMEOUT.putValue(
        10); // loading the traffic file might take longer than 1sec!

    final RequestParameter messageParameters =
        RequestParameter.builder()
            .rbelPath("$.body.Event.Topic.text")
            .value("CT/CONNECTED")
            .requireNewMessage(true)
            .requireRequestMessage(false)
            .build();
    CompletableFuture<RbelElement> waitForMessageFuture =
        CompletableFuture.supplyAsync(
            () -> rbelMessageValidator.waitForMessageToBePresent(messageParameters));

    assertThatThrownBy(() -> waitForMessageFuture.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);

    readTgrFileAndStoreForRbelMessageValidator("src/test/resources/testdata/cetpExampleFlow.tgr");

    assertThat(waitForMessageFuture.get())
        .hasFacet(RbelCetpFacet.class)
        .doesNotHaveFacet(RbelHttpMessageFacet.class)
        .extractChildWithPath("$..Topic.text")
        .hasStringContentEqualTo("CT/CONNECTED");
  }

  @Test
  void parsingTakesSuperLong_MessageShouldStillBeFoundIfReceivedBeforeCall() {
    addMessagePair();

    CompletableFuture<Object> waitForParsing = new CompletableFuture<>();
    doAnswer(
            invocation -> {
              waitForParsing.join();
              return null;
            })
        .when(tigerProxy)
        .waitForAllCurrentMessagesToBeParsed();

    final Thread searchThread =
        new Thread(
            () ->
                rbelMessageValidator.filterRequestsAndStoreInContext(
                    RequestParameter.builder()
                        .path(".*")
                        .filterPreviousRequest(true)
                        .build()
                        .resolvePlaceholders()));

    searchThread.start();

    await().until(() -> searchThread.getState() == Thread.State.WAITING);
    waitForParsing.complete(null);

    assertThat(rbelMessageValidator.currentResponse)
        .extractChildWithPath("$.responseCode")
        .hasStringContentEqualTo("200");
  }

  @Test
  void interleavedRequests_nextMessageShouldFindCorrectMessage() {
    readTgrFileAndStoreForRbelMessageValidator(
        "src/test/resources/testdata/interleavedRequests.tgr");

    validatableMessagesMock.stream().map(RbelElement::printHttpDescription).forEach(log::info);

    // first request
    rbelMessageValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder().rbelPath("$.path").value("/VAU").build());

    logCurrentRequestAndResponse();

    // next request, which comes immediately after the first one, no response in between
    rbelMessageValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .startFromLastMessage(true)
            .build()
            .resolvePlaceholders());

    logCurrentRequestAndResponse();

    assertThat(rbelMessageValidator.currentRequest)
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo("/1716066754997");
  }

  private void logCurrentRequestAndResponse() {
    log.atInfo()
        .addArgument(() -> http(rbelMessageValidator.currentRequest))
        .log("current request: {} ");
    log.atInfo()
        .addArgument(() -> http(rbelMessageValidator.currentResponse))
        .log("current response: {} ");
  }

  @ParameterizedTest
  @MethodSource(value = "encodeAsTestParameters")
  void testRbelWriteEncodeAs_shouldEncodeStringAsGivenContentType(
      String toEncode, String contentType, String expectedResult) {

    TigerGlobalConfiguration.putValue("toEncode", toEncode);
    final String input = "!{rbel:encodeAs(getValue('toEncode'), '" + contentType + "')}";
    String output = TigerGlobalConfiguration.resolvePlaceholders(input);
    assertThat(output).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @MethodSource(value = "encodeAsSignedTokensTestParameters")
  void testRbelWriteEncodeAsSignedTokens_ShouldEncodeStringWithCorrectFormat(
      String toEncode, String contentType, String expectedPattern) {

    TigerGlobalConfiguration.putValue("toEncode", toEncode);
    final String input = "!{rbel:encodeAs(getValue('toEncode'), '" + contentType + "')}";
    String output = TigerGlobalConfiguration.resolvePlaceholders(input);
    // Not checking the exact output, just that it matches the expected pattern
    assertThat(output).matches(expectedPattern);
  }

  public static Stream<Arguments> encodeAsSignedTokensTestParameters() {
    return Stream.of(
        Arguments.of(
            """
{
  "header": {
    "alg": "BP256R1",
    "typ": "JWT"
  },
  "body": {
    "name": "Max Power",
    "iat": 123456,
    "exp": 123456
  },
  "signature": {
    "verifiedUsing": "idpSig"
  }
}""",
            "JWT",
            "^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]*$"),
        Arguments.of(
            """
            {
              "header": {
                "alg": "ECDH-ES",
                "enc": "A256GCM"
              },
              "body": {
                "some_claim": "foobar",
                "other_claim": "code"
              },
              "encryptionInfo": {
                "decryptedUsingKeyWithId": "idpSig"
              }
            }""",
            "JWE",
            "^[A-Za-z0-9-_]+\\.\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$"));
  }

  public static Stream<Arguments> encodeAsTestParameters() {
    return Stream.of(
        Arguments.of(
            "<hello something='world1'>world2</hello>",
            "XML",
            """
                        <?xml version="1.0" encoding="UTF-8"?>

                        <hello something="world1">world2</hello>
                        """),
        Arguments.of("{\"hello\":\"world\"}", "JSON", "{\"hello\": \"world\"}"),
        Arguments.of(
            """
            {
              "tgrEncodeAs": "url",
              "basicPath": "http://bluzb/fdsa",
              "parameters": {
                "foo": "bar"
              }
            }
            """,
            "URL",
            "http://bluzb/fdsa?foo=bar"),
        Arguments.of("{\"BearerToken\":\"blub\"}", "BEARER_TOKEN", "Bearer blub"));
  }

  private String http(RbelElement currentRequest) {
    if (currentRequest != null) {
      return currentRequest.printHttpDescription();
    } else {
      return "<no message>";
    }
  }

  private RbelMessageValidator addMessagePair() {
    // parse in signature cert
    final String keyMessage =
        readCurlFromFileWithCorrectedLineBreaks("idpSigMessage.curl", StandardCharsets.UTF_8);
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    validatableMessagesMock.add(
        rbelConverter.parseMessage(
            keyMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now())));

    // now add signed response as current response
    RbelMessageValidator validator = rbelMessageValidator;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks("getChallenge.curl", StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(
            challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    rbelMessageValidator.currentResponse = convertedMessage;
    validatableMessagesMock.add(convertedMessage);

    return validator;
  }
}
