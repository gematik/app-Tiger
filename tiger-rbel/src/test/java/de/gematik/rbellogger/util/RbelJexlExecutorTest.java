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
package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.TestUtils.localhostWithPort;
import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RbelJexlExecutorTest {

  private RbelElement response;
  private RbelElement request;

  @BeforeEach
  public void setUp() throws IOException {
    final RbelLogger rbelLogger = RbelLogger.build();
    final RbelHostname serverHostname = RbelHostname.fromString("server:1234").get();
    final RbelHostname clientHostname = RbelHostname.fromString("client:54321").get();
    request =
        rbelLogger
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/getRequest.curl")
                    .getBytes(),
                new RbelMessageMetadata().withReceiver(serverHostname).withSender(clientHostname));
    response =
        rbelLogger
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/rbelPath.curl")
                    .getBytes(),
                new RbelMessageMetadata().withSender(serverHostname).withReceiver(clientHostname));
    // Hacky, but all pairing converters are part of tiger-proxy
    request.addOrReplaceFacet(new TracingMessagePairFacet(response, request));
    response.addOrReplaceFacet(new TracingMessagePairFacet(response, request));
  }

  @Test
  void checkRequestMapElements() {
    assertThat(TigerJexlExecutor.matchesAsJexlExpression(request, "isRequest", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "isResponse == false", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "request == message", Optional.empty()))
        .isTrue();
  }

  @Test
  void checkResponseStatusCode() {
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "response.statusCode == 200", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "response.statusCode == 400", Optional.empty()))
        .isFalse();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                response, "response.statusCode == 200", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "request.statusCode == null", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                response, "request.statusCode == null", Optional.empty()))
        .isTrue();
  }

  @Test
  void checkResponseMapElements() {
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                response, "isRequest == false", Optional.empty()))
        .isTrue();
    assertThat(TigerJexlExecutor.matchesAsJexlExpression(response, "isResponse", Optional.empty()))
        .isTrue();
    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                response, "request != message", Optional.empty()))
        .isTrue();
  }

  @Test
  void checkJexlParsingForDoubleHeaders() throws IOException {
    RbelElement doubleHeaderMessage =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/doubleHeader.curl")
                    .getBytes(),
                new RbelMessageMetadata());

    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                doubleHeaderMessage, "isResponse", Optional.empty()))
        .isTrue();
  }

  @Test
  void shouldFindReceiverPort() throws IOException {
    RbelElement request =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/getRequest.curl")
                    .getBytes(),
                new RbelMessageMetadata()
                    .withSender(localhostWithPort(44444))
                    .withReceiver(localhostWithPort(5432))
                    .withTransmissionTime(ZonedDateTime.now()));

    assertThat(
            TigerJexlExecutor.matchesAsJexlExpression(
                request, "$.receiver.port == '5432'", Optional.empty()))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "Keep-Alive"})
  void checkMatchTextExpression(String expression) {
    assertThat(RbelJexlExecutor.matchAsTextExpression(request, expression)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "\\w+host,true",
    "[a-zA-Z0-9_]+hos,true",
    ".*Connection,true",
    "Keep[-/_]Alive,true",
    "Keep-[a-zA-Z0-9_]+Alive,false"
  })
  void checkMatchRegexExpression(String expression, boolean shouldMatch) {
    assertThat(RbelJexlExecutor.matchAsTextExpression(request, expression)).isEqualTo(shouldMatch);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "$..signature.isValid == 'true'",
        "$.body.header=~'.*discSig.*'",
        "$.body.header =~ '.*discSig.*'",
        "$.body.body.scopes_supported.[?(@.content == 'e-rezept')] =~ '.*'",
        "$.body.body.scopes_supported.[?(@.content == 'e-rezept')] =~ '.*'",
        "$.header.['nbf'] =~ '.*'",
        "$.header.['Cache-Control'] =~ 'max-age=300'",
        "$.header.[~'cache-control'] =~ 'max-age=300'",
        "$..['urn:telematik:claims:email'] == 'emailValue'",
        "$..kid.* =~ 'discSig'",
        "$.foo.bar.not.there == null"
      })
  void testVariousJexlExpressions(String jexlExpression) {
    assertThat(TigerJexlExecutor.matchesAsJexlExpression(response, jexlExpression)).isTrue();
  }

  @Test
  void tokenSubstituteHelperRbelPathExtension() {
    assertThat(
            TokenSubstituteHelper.substitute(
                "?{$.body.header}",
                null,
                Optional.of(new TigerJexlContext().withRootElement(response))))
        .contains("discSig");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
         $..scopes.[?(@.content=='test')] =~ '.*',$..scopes.[?(@.content=='test')]
         $..scopes.[?(@.content == 'test')] =~ '.*',$..scopes.[?(@.content == 'test')]
         $..scopes.[?(@.content == 'test')]=~'.*',$..scopes.[?(@.content == 'test')]
         $..scopes.[?(@.content == 'test')],$..scopes.[?(@.content == 'test')]
         $.header.['Cache-Control'] =~ 'max-age=300',$.header.['Cache-Control']
         $.header.['Cache-Control'].blub =~ 'max-age=300',$.header.['Cache-Control'].blub
         $.body.['urn:telematik:claims:email'].test =~ 'max-age=300',$.body.['urn:telematik:claims:email'].test
         $.body.['urn:telematik:claims:email'].* =~ 'max-age=300',$.body.['urn:telematik:claims:email'].*
         $.header.['Content-Type'] =~ 'max-age=300',$.header.['Content-Type']
         $.header.Content-Type =~ 'max-age=300',$.header.Content-Type
         $.body.['xmlns:soap'] =~ 'blabliblu',$.body.['xmlns:soap']
         $.header.[~'cache-control'] =~ 'blabliblu',$.header.[~'cache-control']
        """)
  void testRbelPathExtractor(String jexlExpression, String firstRbelPath) {
    assertThat(RbelJexlExecutor.extractPotentialRbelPaths(jexlExpression))
        .containsOnly(firstRbelPath);
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
         content == 'test'
         content=='test'
         $content =='test'
         @content== 'test'
         $content=='test'
         @content=='test'
        """)
  void testRbelPathExtractorEmptyResults(String jexlExpression) {
    assertThat(RbelJexlExecutor.extractPotentialRbelPaths(jexlExpression)).isEmpty();
  }

  @Test
  void nonUniqueExpression_expectException() {
    assertThatThrownBy(
            () ->
                TigerJexlExecutor.evaluateJexlExpression(
                    "$.. == 'test'", new TigerJexlContext().withRootElement(request)))
        .isInstanceOf(TigerJexlException.class)
        .hasMessageContaining(
            "Evaluated '$.. == 'test'' and got more then one result. Expected one ore zero"
                + " results.");
  }
}
