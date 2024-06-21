/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.addTwoRequestsToTigerTestHooks;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.buildRequestFromCurlFile;
import static de.gematik.test.tiger.lib.rbel.TestsuiteUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelCetpFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.lib.enums.ModeType;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
class RbelMessageValidatorTest {

  private final TigerProxy tigerProxy = mock(TigerProxy.class);

  private static final Deque<RbelElement> validatableMessagesMock = new ArrayDeque<>();
  private static RbelMessageValidator rbelMessageValidator;

  @BeforeEach
  public void clearConfig() {
    TigerGlobalConfiguration.reset();
    validatableMessagesMock.clear();
    LocalProxyRbelMessageListener.setTestingInstance(
        new LocalProxyRbelMessageListener(
            new RbelMessagesSupplier() {
              @Override
              public void addRbelMessageListener(IRbelMessageListener listener) {
                // do nothing
              }

              @Override
              public Deque<RbelElement> getRbelMessages() {
                return validatableMessagesMock;
              }
            }));

    when(tigerProxy.getRbelMessages()).thenReturn(validatableMessagesMock);
    rbelMessageValidator = new RbelMessageValidator(mock(TigerTestEnvMgr.class), tigerProxy);

    rbelMessageValidator.currentRequest = null;
    rbelMessageValidator.currentResponse = null;
    LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
  }

  @AfterEach
  public void cleanUp() {
    LocalProxyRbelMessageListener.clearTestingInstance();
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
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "localhost:8080"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegex_OK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "local.*:8080"))
        .isTrue();
  }

  @Test
  void testHostMatchingRegexNotMatching_OK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "eitzen.*"))
        .isFalse();
  }

  @Test
  void testHostMatchingInvalidRegex_NOK() {
    assertThat(
            rbelMessageValidator.doesHostMatch(
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "["))
        .isFalse();
  }

  @Test
  void testMethodMatching_OK() {
    assertThat(
            rbelMessageValidator.doesMethodMatch(
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "GET"))
        .isTrue();
  }

  @Test
  void testMethodMatchingNotMatching_OK() {
    assertThat(
            rbelMessageValidator.doesMethodMatch(
                buildRequestFromCurlFile("getRequestLocalhost.curl"), "POST"))
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
            () -> {
              rbelMessageValidator.compareXMLStructure(
                  "<root><header></header><body><!-- test comment --></body></root>",
                  "<root><header></header><body></body></root>",
                  "nocomment");
            });
  }

  @Test
  void testSourceNoCommetTxtTrim_OK() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              rbelMessageValidator.compareXMLStructure(
                  "<root><header></header><body>test     <!-- test comment --></body></root>",
                  "<root><header></header><body>test</body></root>",
                  "nocomment,txttrim");
            });
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
    RbelElement request = validator.currentRequest;
    assertTrue(validator.doesHostMatch(request, "localhost:8080"));
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
        RequestParameter.builder().path(".*").startFromLastRequest(true).build());
    RbelElement request = validator.currentRequest;

    assertTrue(validator.doesHostMatch(request, "eitzen.at:80"));
  }

  @Test
  void testFindLastRequest_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.findLastRequest();
    RbelElement request = validator.currentRequest;
    assertTrue(validator.doesHostMatch(request, "eitzen.at:80"));
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
    assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
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
    assertThatThrownBy(
            () -> {
              rbelMessageValidator.filterRequestsAndStoreInContext(reuqest);
            })
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
    assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
  }

  @Test
  void testFilterRequestsRbelPathExists_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.User-Agent").build());
    assertTrue(validator.doesHostMatch(validator.currentRequest, "localhost:8080"));
  }

  @Test
  void testFilterRequestsRbelPathExists2_OK() {
    addTwoRequestsToTigerTestHooks(validatableMessagesMock);
    RbelMessageValidator validator = rbelMessageValidator;
    validator.filterRequestsAndStoreInContext(
        RequestParameter.builder().path(".*").rbelPath("$.header.Eitzen-Specific-header").build());
    assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
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
    assertTrue(validator.doesHostMatch(validator.currentRequest, "eitzen.at:80"));
    assertThat(
            validator
                .currentResponse
                .getFacet(RbelHttpResponseFacet.class)
                .get()
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
            () -> {
              validator.assertAttributeOfCurrentResponseMatches(
                  "$.body.challenge.content.signature.isValid", "true", false);
            })
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () -> {
              validator.assertAttributeOfCurrentResponseMatches(
                  "$.body.challenge.content.signature.isValid", "false", true);
            })
        .isInstanceOf(AssertionError.class);
    assertThatThrownBy(
            () -> {
              validator.findAnyMessageMatchingAtNode(
                  "$.body.challenge.content.signature.isValid", "false");
            })
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
    validator.currentResponse = convertedMessage;
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
    validator.currentRequest = convertedMessage;
    validatableMessagesMock.add(convertedMessage);
    validator.findElementsInCurrentRequest("$.body.foo");
    validator.assertAttributeOfCurrentRequestMatches("$.body.foo", "bar", true);
    String oracleStr = "{'foo': '${json-unit.ignore}'}";
    validator.assertAttributeOfCurrentRequestMatchesAs("$.body", ModeType.JSON, oracleStr);

    log.info("Current Request: {}", validator.currentRequest);
    log.info("converted message: {}", convertedMessage);

    RBelValidatorGlue glue = new RBelValidatorGlue(rbelMessageValidator);
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

    RBelValidatorGlue glue = new RBelValidatorGlue(rbelMessageValidator);
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
    validator.currentRequest = convertedMessage;
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
    validator.currentRequest = convertedMessage;
    validatableMessagesMock.add(convertedMessage);
    assertThatThrownBy(
            () -> validator.assertAttributeOfCurrentRequestMatches("$.body.foo", "blabla", true))
        .isInstanceOf(AssertionError.class);
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
    final RequestParameter messageParameters =
        RequestParameter.builder()
            .rbelPath("$.body.Event.Topic.text")
            .value("CT/CONNECTED")
            .requireNewMessage(true)
            .requireHttpMessage(false)
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

    assertThat(rbelMessageValidator.getCurrentResponse())
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

    log.info("current request: {} ", http(rbelMessageValidator.currentRequest));
    log.info("current response: {} ", http(rbelMessageValidator.currentResponse));

    // next request, which comes immediately after the first one, no response in between
    rbelMessageValidator.filterRequestsAndStoreInContext(
        RequestParameter.builder()
            .path(".*")
            .startFromLastRequest(true)
            .build()
            .resolvePlaceholders());

    log.info("current request: {} ", http(rbelMessageValidator.currentRequest));
    log.info("current response: {} ", http(rbelMessageValidator.currentResponse));

    assertThat(rbelMessageValidator.getCurrentRequest())
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo("/1716066754997");
  }

  private String http(RbelElement currentRequest) {
    if (currentRequest != null) {
      return currentRequest.printHttpDescription();
    } else {
      return "<no message>";
    }
  }

  private static RbelMessageValidator addMessagePair() {
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
    validator.currentResponse = convertedMessage;
    validatableMessagesMock.add(convertedMessage);

    return validator;
  }

  private static void readTgrFileAndStoreForRbelMessageValidator(String rbelFile) {
    var rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(RbelFileReaderCapturer.builder().rbelFile(rbelFile).build()));
    rbelLogger.getRbelCapturer().initialize();
    validatableMessagesMock.addAll(rbelLogger.getMessageHistory());
  }
}
