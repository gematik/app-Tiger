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
package de.gematik.test.tiger.lib.rbel;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.util.CurlTestdataUtil.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.cetp.RbelCetpFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
class RbelMessageRetrieverTest extends AbstractRbelMessageValidatorTest {

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();
  }

  @Test
  void testPathEqualsWithRelativePath_OK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("/foo/bar?sch=mar"), "/foo/bar"))
        .isTrue();
  }

  @Test
  void testPathEqualsWithUrl_OK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "/foo/bar"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlLeading_OK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/.*\\/bar"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlTrailing_OK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar?sch=mar"), "\\/foo\\/.*"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithUrlInMid_OK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "\\/foo\\/.*/test"))
        .isTrue();
  }

  @Test
  void testPathMatchingWithNotMatchRegex_NOK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "/foo/.*/[test]"))
        .isFalse();
  }

  @Test
  void testPathMatchingWithInvalidRegex_NOK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(
                buildRequestWithPath("http://bl.ub/foo/bar/test?sch=mar"), "["))
        .isFalse();
  }

  @Test
  void testInvalidPathMatching_NOK() {
    assertThat(
            rbelMessageRetriever.doesPathOfMessageMatch(buildRequestWithPath("file$:."), "/foo/.*"))
        .isFalse();
  }

  private RbelElement buildRequestWithPath(final String path) {
    final RbelElement rbelElement = new RbelElement();
    rbelElement.addFacet(
        RbelHttpRequestFacet.builder().path(new RbelElement(path.getBytes(), null)).build());
    return rbelElement;
  }

  @Test
  void testHostMatching_OK() {
    assertThat(
            rbelMessageRetriever.doesHostMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "localhost"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegex_OK() {
    assertThat(
            rbelMessageRetriever.doesHostMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "local.*"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegexNotMatching_OK() {
    assertThat(
            rbelMessageRetriever.doesHostMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "eitzen.*"))
        .isFalse();
  }

  @Test
  void testHostMatchingInvalidRegex_NOK() {
    assertThat(
            rbelMessageRetriever.doesHostMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "["))
        .isFalse();
  }

  @Test
  void testMethodMatching_OK() {
    assertThat(
            rbelMessageRetriever.doesMethodMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "GET"))
        .isTrue();
  }

  @Test
  void testMethodMatchingNotMatching_OK() {
    assertThat(
            rbelMessageRetriever.doesMethodMatch(
                localProxyRbelMessageListenerTestAdapter
                    .buildElementsFromTgrFile("simpleHttpRequests.tgr")
                    .get(0),
                "POST"))
        .isFalse();
  }

  @ParameterizedTest
  @CsvSource({"http://server, ''", "http://server/, /", "http://server, /", "http://server/, ''"})
  void testEmptyPathMatching(final String url, final String path) {
    assertThat(rbelMessageRetriever.doesPathOfMessageMatch(buildRequestWithPath(url), path))
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"http://server/blu/, /", "http://server/, /bla", "http://server/bla, ''"})
  void testPathOfMessageMatch_NOK(final String url, final String path) {
    assertThat(rbelMessageRetriever.doesPathOfMessageMatch(buildRequestWithPath(url), path))
        .isFalse();
  }

  @Test
  void testFindMessage_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());
    RbelElement request = rbelMessageRetriever.currentRequest;
    assertTrue(validator.doesHostMatch(request, "localhost"));
  }

  @Test
  void testFindMessageWrongPath_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    final RequestParameter requestParameter = RequestParameter.builder().path("/NOWAY.*").build();
    assertThatThrownBy(() -> validator.filterRequestsAndStoreInContext(requestParameter))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindMessageNextRequest_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(RequestParameter.builder().path(".*").build());

    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").startFromLastMessage(true).build());
    RbelElement request = rbelMessageRetriever.currentRequest;

    assertTrue(validator.doesHostMatch(request, "eitzen.at"));
  }

  @Test
  void testFindLastRequest_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.findLastRequest();
    RbelElement request = rbelMessageRetriever.currentRequest;
    assertTrue(validator.doesHostMatch(request, "eitzen.at"));
  }

  @Test
  void testFindMessageRbelPath_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonalagent")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageRetriever.currentRequest, "eitzen.at"));
  }

  @Test
  void testFindMessageRbelPathNotMatching_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    var reuqest =
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonalagentXXXX")
            .build();
    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(reuqest))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindMessageRbelPathRegex_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonal.*")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageRetriever.currentRequest, "eitzen.at"));
  }

  @Test
  void testFindMessageRbelPathExists_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").build());
    assertTrue(validator.doesHostMatch(rbelMessageRetriever.currentRequest, "localhost"));
  }

  @Test
  void testFindMessageRbelPathExists2_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertTrue(validator.doesHostMatch(rbelMessageRetriever.currentRequest, "eitzen.at"));
  }

  @Test
  void testFindMessageRbelPathExists_NOK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    var request = RequestParameter.builder().path(".*").rbelPath("$.header.User-AgentXXX").build();
    assertThatThrownBy(() -> rbelMessageRetriever.filterRequestsAndStoreInContext(request))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindMessageAttachResponseCorrectly_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .rbelPath("$.header.User-Agent")
            .value("mypersonal.*")
            .build());
    assertTrue(validator.doesHostMatch(rbelMessageRetriever.currentRequest, "eitzen.at"));
    assertThat(
            rbelMessageRetriever
                .currentResponse
                .getFacetOrFail(RbelHttpResponseFacet.class)
                .getResponseCode()
                .getRawStringContent())
        .isEqualTo("500");
  }

  @Test
  void testFindElementInCurrentResponse_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    Assertions.assertThat(validator.findElementInCurrentResponse("$.body..h1"))
        .isInstanceOf(RbelElement.class);
  }

  @Test
  void testFindElementInCurrentResponse_NOK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertThatThrownBy(() -> validator.findElementInCurrentResponse("$.body..h2"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testFindElementInCurrentRequest_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    Assertions.assertThat(validator.findElementInCurrentRequest("$..User-Agent"))
        .isInstanceOf(RbelElement.class);
  }

  @Test
  void testFindElementInCurrentRequest_NOK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertThatThrownBy(() -> validator.findElementInCurrentRequest("$..UnknownHeader"))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testValidatorAllowsToMatchNodesBeingBooleanRbelValues_True() {
    // parse in signature cert
    final RbelMessageRetriever validator = addMessagePair();

    // validate
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "true", true, rbelMessageRetriever);
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "false", false, rbelMessageRetriever);
    validator.findAnyMessageMatchingAtNode("$.body.challenge.content.signature.isValid", "true");
    assertThatThrownBy(
            () ->
                rbelValidator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid",
                    "true",
                    false,
                    rbelMessageRetriever))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                rbelValidator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid",
                    "false",
                    true,
                    rbelMessageRetriever))
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
    RbelMessageRetriever validator = rbelMessageRetriever;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/getChallenge.curl");
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(challengeMessage.getBytes(), new RbelMessageMetadata());
    rbelMessageRetriever.currentResponse = convertedMessage;
    localProxyRbelMessageListenerTestAdapter.addMessage(convertedMessage);

    // validate
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "false", true, rbelMessageRetriever);
    rbelValidator.assertAttributeOfCurrentResponseMatches(
        "$.body.challenge.content.signature.isValid", "true", false, rbelMessageRetriever);
    validator.findAnyMessageMatchingAtNode("$.body.challenge.content.signature.isValid", "false");
    assertThatThrownBy(
            () ->
                rbelValidator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid",
                    "false",
                    false,
                    rbelMessageRetriever))
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () ->
                rbelValidator.assertAttributeOfCurrentResponseMatches(
                    "$.body.challenge.content.signature.isValid",
                    "true",
                    true,
                    rbelMessageRetriever))
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
    RbelMessageRetriever validator = rbelMessageRetriever;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/getCurrentRequest.curl");
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(challengeMessage.getBytes(), new RbelMessageMetadata());
    rbelMessageRetriever.currentRequest = convertedMessage;
    localProxyRbelMessageListenerTestAdapter.addMessage(convertedMessage);
    validator.findElementsInCurrentRequest("$.body.foo");
    rbelValidator.assertAttributeOfCurrentRequestMatches(
        "$.body.foo", "bar", true, rbelMessageRetriever);
    String oracleStr = "{'foo': '${json-unit.ignore}'}";
    rbelValidator.assertAttributeOfCurrentRequestMatchesAs(
        "$.body", ModeType.JSON, oracleStr, rbelMessageRetriever);

    log.info("Current Request: {}", rbelMessageRetriever.currentRequest);
    log.info("converted message: {}", convertedMessage);

    glue.currentRequestBodyMatches("!{rbel:currentRequestAsString('$.body')}");
    glue.currentRequestMessageAttributeMatches("$.body.foo", "bar");
    glue.currentRequestMessageContainsNode("$.body.foo");
    glue.currentRequestMessageAtMatchesDocString("$.body", "{\"foo\":\"bar\"}");
    glue.currentRequestAtMatchesAsJsonOrXml("$.body", ModeType.JSON, oracleStr);
    glue.currentRequestMessageAttributeDoesNotMatch("$.body.foo", "foo");
  }

  @Test
  void testCurrentRequestMatchesJsonSchemaWithPlaceholdersReplacement() {
    val responseToCheck =
        """
        HTTP/1.1 200 OK
        Content-Length: 18

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

    rbelMessageRetriever.currentRequest =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(responseToCheck.getBytes(), new RbelMessageMetadata());

    glue.currentRequestAtMatchesAsJsonOrXml("$.body", ModeType.JSON_SCHEMA, schema);

    TigerGlobalConfiguration.reset();
  }

  @Test
  void testCurrentRequestDoesNotMatchAsExpected() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/getCurrentRequest.curl");
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(challengeMessage.getBytes(), new RbelMessageMetadata());
    rbelMessageRetriever.currentRequest = convertedMessage;
    localProxyRbelMessageListenerTestAdapter.addMessage(convertedMessage);

    rbelValidator.assertAttributeOfCurrentRequestMatches(
        "$.body.foo", "blala", false, rbelMessageRetriever);
  }

  @Test
  void testCurrentRequestMatchesFailure() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/getCurrentRequest.curl");
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(challengeMessage.getBytes(), new RbelMessageMetadata());
    rbelMessageRetriever.currentRequest = convertedMessage;
    localProxyRbelMessageListenerTestAdapter.addMessage(convertedMessage);
    assertThatThrownBy(
            () ->
                rbelValidator.assertAttributeOfCurrentRequestMatches(
                    "$.body.foo", "blabla", true, rbelMessageRetriever))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void testCurrentMessagesNotFound() {
    readTgrFileAndStoreForRbelMessageRetriever(
        "src/test/resources/testdata/interleavedRequests.tgr");
    RbelMessageRetriever validator = rbelMessageRetriever;

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
    readTgrFileAndStoreForRbelMessageRetriever("src/test/resources/testdata/cetpExampleFlow.tgr");

    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().rbelPath("$.body.Event.Topic.text").build());

    assertDoesNotThrow(() -> rbelMessageRetriever.findElementInCurrentRequest("$..Topic.text"));

    var assertionError =
        assertThrows(
            AssertionError.class, () -> rbelMessageRetriever.findElementInCurrentResponse("$"));
    assertThat(assertionError.getMessage()).isEqualTo("No current response message found!");
  }

  @Test
  void testFindMultipleNodesInRequestShouldMatch_OK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    gluecode.currentResponseMessageAttributeMatches("$..td.a.text", "apidocs.*");
  }

  @Test
  void testFindMultipleNodesInRequestShouldMatch_NOK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
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
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
    RBelValidatorGlue gluecode = new RBelValidatorGlue(validator);

    gluecode.findNextRequestToPath("/auth/realms/idp/.well-known/openid-configuration");
    gluecode.currentResponseMessageAttributeDoesNotMatch(
        "$..td.a.text", "SOMETHINGTHATNEVERMATCHES.*");
  }

  @Test
  void testFindMultipleNodesInRequestShouldNotMatch_NOK() {
    localProxyRbelMessageListenerTestAdapter.addTwoRequestsToTigerTestHooks();
    RbelMessageRetriever validator = rbelMessageRetriever;
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
            () -> rbelMessageRetriever.waitForMessageToBePresent(messageParameters));

    readTgrFileAndStoreForRbelMessageRetriever("src/test/resources/testdata/cetpExampleFlow.tgr");

    waitForMessageFuture.get();
    assertThat(rbelMessageRetriever.findMessageByDescription(messageParameters))
        .extractChildWithPath("$..Topic.text")
        .hasStringContentEqualTo("CT/CONNECTED");
  }

  @Test
  void testWaitingForNewNonPairedMessage() throws ExecutionException, InterruptedException {

    RbelMessageRetriever.RBEL_REQUEST_TIMEOUT.putValue(
        10); // loading the traffic file might take longer than 1sec!
    try {
      final RequestParameter messageParameters =
          RequestParameter.builder()
              .rbelPath("$.body.Event.Topic.text")
              .value("CT/CONNECTED")
              .requireNewMessage(true)
              .requireRequestMessage(false)
              .build();
      CompletableFuture<RbelElement> waitForMessageFuture =
          CompletableFuture.supplyAsync(
              () -> rbelMessageRetriever.waitForMessageToBePresent(messageParameters));

      assertThatThrownBy(() -> waitForMessageFuture.get(500, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      readTgrFileAndStoreForRbelMessageRetriever("src/test/resources/testdata/cetpExampleFlow.tgr");

      assertThat(waitForMessageFuture.get())
          .hasFacet(RbelCetpFacet.class)
          .doesNotHaveFacet(RbelHttpMessageFacet.class)
          .extractChildWithPath("$..Topic.text")
          .hasStringContentEqualTo("CT/CONNECTED");
    } finally {
      RbelMessageRetriever.RBEL_REQUEST_TIMEOUT.clearValue();
    }
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
                rbelMessageRetriever.filterRequestsAndStoreInContext(
                    RequestParameter.builder()
                        .path(".*")
                        .filterPreviousRequest(true)
                        .build()
                        .resolvePlaceholders()));

    searchThread.start();

    await().until(() -> searchThread.getState() == Thread.State.WAITING);
    waitForParsing.complete(null);

    assertThat(rbelMessageRetriever.currentResponse)
        .extractChildWithPath("$.responseCode")
        .hasStringContentEqualTo("200");
  }

  @Test
  void interleavedRequests_nextMessageShouldFindCorrectMessage() {
    readTgrFileAndStoreForRbelMessageRetriever(
        "src/test/resources/testdata/interleavedRequests.tgr");

    localProxyRbelMessageListenerTestAdapter.getValidatableMessagesMock().stream()
        .map(RbelElement::printHttpDescription)
        .forEach(log::info);

    // first request
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder().rbelPath("$.path").value("/VAU").build());

    logCurrentRequestAndResponse();

    // next request, which comes immediately after the first one, no response in between
    rbelMessageRetriever.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .startFromLastMessage(true)
            .build()
            .resolvePlaceholders());

    logCurrentRequestAndResponse();

    assertThat(rbelMessageRetriever.currentRequest)
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo("/1716066754997");
  }

  private void logCurrentRequestAndResponse() {
    log.atInfo()
        .addArgument(() -> http(rbelMessageRetriever.currentRequest))
        .log("current request: {} ");
    log.atInfo()
        .addArgument(() -> http(rbelMessageRetriever.currentResponse))
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
            }\
            """,
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
            }\
            """,
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

  private RbelMessageRetriever addMessagePair() {
    // parse in signature cert
    final String keyMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/idpSigMessage.curl");
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
    localProxyRbelMessageListenerTestAdapter.addMessage(
        rbelConverter.parseMessage(keyMessage.getBytes(), new RbelMessageMetadata()));

    // now add signed response as current response
    RbelMessageRetriever validator = rbelMessageRetriever;
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/testdata/sampleCurlMessages/getChallenge.curl");
    final RbelElement convertedMessage =
        rbelConverter.parseMessage(challengeMessage.getBytes(), new RbelMessageMetadata());
    rbelMessageRetriever.currentResponse = convertedMessage;
    localProxyRbelMessageListenerTestAdapter.addMessage(convertedMessage);

    return validator;
  }

  @Test
  void testSchemaValidationFailureMessagePropagated() {
    readTgrFileAndStoreForRbelMessageRetriever("src/test/resources/testdata/jsonSchemaCheck.tgr");

    RequestParameter rootElementRequest =
        RequestParameter.builder()
            .rbelPath("$.body.regStat")
            .requireRequestMessage(false)
            .startFromLastMessage(false)
            .build();
    rbelMessageRetriever.filterRequestsAndStoreInContext(rootElementRequest);
    assertThat(rbelMessageRetriever.getCurrentResponse()).isNotNull();

    var assertionError =
        assertThrows(
            AssertionError.class,
            () ->
                rbelValidator.assertAttributeOfCurrentResponseMatchesAs(
                    "$.body",
                    ModeType.JSON_SCHEMA,
                    """
                      {
                         "$schema": "https://json-schema.org/draft/2020-12/schema",
                         "description": "a description",
                         "required" : [
                           "regStat",
                           "foobar"
                         ],
                         "properties": {
                           "regStat" : {
                             "type" : "string",
                             "enum" : [ "registerede", "deregistered" ]
                           }
                         }
                       }
                    """,
                    "",
                    rbelMessageRetriever));

    assertThat(assertionError.getMessage())
        .containsAnyOf(
            "/regStat: does not have a value in the enumeration",
            "/regStat: hat keinen Wert in der Aufzählung")
        .containsAnyOf(
            "required property 'foobar' not found",
            "erforderliche Eigenschaft 'foobar' nicht gefunden");
  }

  /**
   * tests bug TGR-1812
   *
   * <p>The schema has a trailing comma. This is allowed javascript, but not allowed JSON.
   *
   * <p>The parser that we use may accept it, if the strictMode validation is turned off.
   *
   * <p>In an old version of JSON-java, a bug would prevent the check to see if the strictMode is on
   * or off, because of a null default configuration of the JSONObject.
   *
   * <p>The test threw java.lang.NullPointerException: Cannot invoke
   * "org.json.JSONParserConfiguration.isStrictMode()" because "jsonParserConfiguration" is null
   *
   * <p>With JSON-java version 20250517 the code no longer throws NPE.
   */
  @Test
  void testCurrentRequestMatchesAsJson() {
    val responseToCheck =
        """
        HTTP/1.1 200 OK
        Content-Length: 18

        {'hello': 'world'}
        """;
    // Schema has a trailing comma which in an older version of JSON-java leads to
    val schema =
        """
        {"hello": 'world',}
        """;

    rbelMessageRetriever.currentRequest =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(responseToCheck.getBytes(), new RbelMessageMetadata());

    assertThatCode(
            () -> {
              glue.currentRequestAtMatchesAsJsonOrXml("$.body", ModeType.JSON, schema);
            })
        .doesNotThrowAnyException();

    TigerGlobalConfiguration.reset();
  }
}
