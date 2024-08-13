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

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.exceptions.RbelPathException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelPathExecutorTest {

  private static final RbelConverter RBEL_CONVERTER =
      RbelLogger.build(new RbelConfiguration().activateConversionFor("asn1"))
          .getRbelConverter();
  private static RbelElement jwtMessage;
  private static RbelElement xmlMessage;
  private static RbelElement jsonElement;

  @BeforeAll
  public static void setUp() throws IOException {
    jwtMessage = extractMessage("rbelPath.curl");
    xmlMessage = extractMessage("xmlMessage.curl");
    jsonElement = extractMessage("../jexlWorkshop.json");
  }

  private static RbelElement extractMessage(String fileName) throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/" + fileName);

    return RBEL_CONVERTER.parseMessage(
        curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
  }

  @Test
  void assertThatPathValueFollowsConvention() {
    assertThat(jwtMessage.findNodePath()).isEmpty();
    assertThat(jwtMessage.getFirst("header").get().findNodePath()).isEqualTo("header");
    assertThat(jwtMessage.getFirst("header").get().getChildNodes().get(0).findNodePath())
        .startsWith("header.");
  }

  @Test
  void simpleRbelPath_shouldFindTarget() {
    assertThat(jwtMessage)
        .extractChildWithPath("$.header")
        .isSameAs(jwtMessage.getFacetOrFail(RbelHttpMessageFacet.class).getHeader());

    assertThat(jwtMessage.findRbelPathMembers("$.body.body.nbf"))
        .containsExactly(
            jwtMessage
                .getFirst("body")
                .get()
                .getFirst("body")
                .get()
                .getFirst("nbf")
                .get()
                .getFirst("content")
                .get());
  }

  @Test
  void rbelPathEndingOnStringValue_shouldReturnNestedValue() {
    assertThat(jwtMessage)
        .extractChildWithPath("$.body.body.sso_endpoint")
        .asString()
        .startsWith("http://");
  }

  @ParameterizedTest
  @CsvSource({
    "$.['body'].['body'].['nbf'], $.body.body.nbf",
    "$.body.[*].nbf, $.body.body.nbf.content", // wildcard
    "$.body.*.nbf, $.body.body.nbf.content", // wildcard
    "$.body..nbf, $.body.body.nbf.content", // recursive descent
    "$..[?(path=~'.*scopes_supported\\.\\d')], $.body.body.scopes_supported.*", // complex JEXL
    "$.body.body..[?(path=~'.*scopes_supported\\.\\d')], $.body.body.scopes_supported.*", // complex JEXL
    "$.body.body.['nbf'|'foobar'], $.body.body.nbf", // alternate keys
    "$.body.body.['foobar'|'nbf'], $.body.body.nbf" // alternate keys
  })
  void testPathsWithJwtMessage(String thisPath, String shouldMatchPath) {
    final List<RbelElement> path1Results = jwtMessage.findRbelPathMembers(thisPath);
    final List<RbelElement> path2Results = jwtMessage.findRbelPathMembers(shouldMatchPath);
    assertThat(path1Results).containsAll(path2Results).isNotEmpty();
    assertThat(path2Results).containsAll(path1Results).isNotEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "$..nbf", // recursive descent
    "$..[?(key=='nbf')]", // simple JEXL
  })
  void testRecursiveDescent(String recursivePath) {
    final List<RbelElement> pathResults = jwtMessage.findRbelPathMembers(recursivePath);
    final List<RbelElement> referenceResults = List.of(
      jwtMessage.findElement("$.header.nbf").get(),
      jwtMessage.findElement("$.body.body.nbf").get()
    );
    assertThat(pathResults).containsAll(referenceResults).isNotEmpty();
    assertThat(referenceResults).containsAll(pathResults).isNotEmpty();
  }

  @Test
  void alternateKeys_shouldFindMultipleTargets() {
    assertThat(jwtMessage.findRbelPathMembers("$.body.body.['exp'|'iat'|'nbf']"))
        .containsExactlyInAnyOrder(
            jwtMessage.findElement("$.body.body.nbf").get(),
            jwtMessage.findElement("$.body.body.exp").get(),
            jwtMessage.findElement("$.body.body.iat").get());
  }

  @Test
  void findAllMembers() {
    assertThat(jwtMessage.findRbelPathMembers("$..*")).hasSize(224);
  }

  @Test
  void findSingleElement_present() {
    assertThat(jwtMessage.findElement("$.body.body.authorization_endpoint"))
        .isPresent()
        .get()
        .isEqualTo(jwtMessage.findRbelPathMembers("$.body.body.authorization_endpoint").get(0));
  }

  @Test
  void findSingleElement_notPresent_expectEmpty() {
    assertThat(jwtMessage.findElement("$.hfd7a89vufd")).isEmpty();
  }

  @Test
  void findSingleElementWithMultipleReturns_expectException() {
    assertThatThrownBy(() -> jwtMessage.findElement("$..*")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void eliminateContentInRbelPathResult() throws IOException {
    final String challengeMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/getChallenge.curl");

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(
                challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    Assertions.assertThat(convertedMessage.findElement("$.body.challenge.signature"))
        .containsSame(convertedMessage.findElement("$.body.challenge.content.signature").get());
  }

  @Test
  void rbelPathWithReasonPhrase_shouldReturnTheValue() {
    assertThat(jwtMessage.findRbelPathMembers("$.reasonPhrase").get(0).getRawStringContent())
        .isEqualTo("OK");
  }

  @ParameterizedTest
  @CsvSource({
    "$..[?(@.alg=='BP256R1')],$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header",
    "$..[?(@.hier=='ist kein"
        + " text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
    "$..RegistryError.[?(@.hier=='ist kein"
        + " text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
    "$..textTest[?(@.hier=='ist kein"
        + " text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
    "$..RegistryError[1].textTest[?(@.hier=='ist kein"
        + " text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
    "$..x5c.0.content.0.7..[?(@.0 == '1.3.36.8.3.3')],$..7.content.1",
    "$.body.RegistryResponse.RegistryErrorList.RegistryError[1].jwtTag,$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag",
  })
  void rbelPathWithAddSign_ShouldFindCorrectNode(String path1, String path2) {
    final List<RbelElement> path1Results = xmlMessage.findRbelPathMembers(path1);
    final List<RbelElement> path2Results = xmlMessage.findRbelPathMembers(path2);
    assertThat(path1Results).containsAll(path2Results).isNotEmpty();
    assertThat(path2Results).containsAll(path1Results).isNotEmpty();
  }

  @Test
  void testRelativeJexlSelectorInJwt() {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/nestedJwtTraffic.tgr")
            .build();
    final RbelLogger logger =
        RbelLogger.build(RbelConfiguration.builder().capturer(fileReaderCapturer).build());
    fileReaderCapturer.initialize();
    final RbelElement secondResponse = logger.getMessageList().get(3);

    assertThat(secondResponse)
        .extractChildWithPath(
            "$.body.body.idp_entity.[?(@.iss.content=='https://idpsek.dev.gematik.solutions')]")
        .hasStringContentEqualTo(
            "{\"iss\":\"https://idpsek.dev.gematik.solutions\",\"organization_name\":\"gematik\",\"logo_uri\":null,\"user_type_supported\":\"IP\"}");
  }

  @Test
  void testAbsoluteJexlSelectorInJwt() {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/nestedJwtTraffic.tgr")
            .build();
    final RbelLogger logger =
        RbelLogger.build(RbelConfiguration.builder().capturer(fileReaderCapturer).build());
    fileReaderCapturer.initialize();
    final RbelElement secondResponse = logger.getMessageList().get(3);

    assertThat(secondResponse)
        .extractChildWithPath(
            "$.body.body.idp_entity.[?(@.iss.content==$.body.body.idp_entity.0.iss.content)]")
        .hasStringContentEqualTo(
            "{\"iss\":\"https://idpsek.dev.gematik.solutions\",\"organization_name\":\"gematik\",\"logo_uri\":null,\"user_type_supported\":\"IP\"}");
  }

  @Test
  void trailingSpace_shouldStillWork() {
    assertThat(jwtMessage)
        .extractChildWithPath("$.body.body ")
        .isSameAs(jwtMessage.findElement("$.body.body").get());
  }

  @Test
  void unescapedSpaceInKeys_shouldGiveError() {
    assertThatThrownBy(() -> jwtMessage.findRbelPathMembers("$.body .body"))
        .isInstanceOf(RbelPathException.class)
        .hasMessageContaining("$.body .body");
  }

  @Test
  void esacpedSpaceInKeys_shouldWorkCorrectly() {
    assertThat(RBEL_CONVERTER.convertElement("{\"foo bar\":\"value\"}", null))
        .extractChildWithPath("$.['foo bar']")
        .hasStringContentEqualTo("value");
  }

  @Test
  void escapedPipeInKeys_shouldFindTarget() {
    assertThat(RBEL_CONVERTER.convertElement("{\"foo|bar\":\"value\"}", null))
        .extractChildWithPath("$.['foo%7Cbar']")
        .hasStringContentEqualTo("value");
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "${myMap.anotherLevel.[?(@.value=='foobar')].target}, schmoo",
        "${myMap..[?(@.target=='schmoo')].target}, schmoo",
        "${MYMAP..[?(@.tArGeT=='schmoo')].target}, schmoo",
        "${MYMAP..[?(@.vAlUe=='xMaS')].target}, tree",
        "${..buried}, deep",
      })
  void testSubstituteTokensFromConfigurationUsingRbelPath(
      String stringToSubstitute, String expectedString) {
    TigerGlobalConfiguration.readFromYaml(
        """
                myMap:
                  anotherLevel:
                    key1:
                      value: foobar
                      target: schmoo
                    key2:
                      value: xMaS
                      target: tree
                  hidden:
                    treasure:
                      buried: deep
          """);
    assertThat(TigerGlobalConfiguration.resolvePlaceholders(stringToSubstitute))
        .isEqualTo(expectedString);
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({
    "$..[?(@.0.id == '5001')], $.topping",
    "$..[?(@.id == '5001')], $.topping.0",
    "$..batter.[?(content =~ \".*1001.*\")], $.batters.batter.0",
    "$..recipient.[?(@.. == 'FooBar')], $.recipient.1",
    "$..recipient.[?(not (@.. == 'FooBar'))], $.recipient.*",
    "$..topping[?(content =~ \".*1001.*\")], $.topping",
    "$..topping[?(content =~ \".*1001.*\")], $.topping",
    "$..topping.*[6], $.topping.6",
    "$..type[?(content =~ \".*Regular.*\")], $.batters.batter.0.type",
  })
  void recursiveDescentMixedWithJexl(String rbelPath1, String rbelPath2) {
    final List<RbelElement> reference = jsonElement.findRbelPathMembers(rbelPath2);
    assertThat(jsonElement.findRbelPathMembers(rbelPath1))
        .containsExactlyInAnyOrderElementsOf(reference);
  }

  @ParameterizedTest
  @CsvSource({"$..['some.other-tag'].text, blub", "$..['urn:telematik:claims:email'], blab"})
  void testEscapingOfSpecialCharacters(String rbelPath, String expectedResult) {
    assertThat(xmlMessage.findRbelPathMembers(rbelPath).get(0).getRawStringContent())
        .isEqualToIgnoringWhitespace(expectedResult);
  }

  @ParameterizedTest
  @CsvSource({
    "$..[~'wRONGgnAmeSpaCe'], $..wrongNamespace",
    "$..[~'blub'|'wRONGgnAmeSpaCe'], $..wrongNamespace",
    "$..[~'wRONGgnAmeSpaCe'|'blub'], $..wrongNamespace"
  })
  void fuzzyMatching_shouldWork(String rbelPath, String expectedResult) {
    assertThat(xmlMessage.findRbelPathMembers(rbelPath))
        .isEqualTo(xmlMessage.findRbelPathMembers(expectedResult));
  }
}
