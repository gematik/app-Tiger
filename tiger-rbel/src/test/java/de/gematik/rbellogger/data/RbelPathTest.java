/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelPathExecutor;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

public class RbelPathTest {

    private RbelElement jwtMessage;
    private RbelElement xmlMessage;

    @BeforeEach
    public void setUp() throws IOException {
        RbelOptions.activateRbelPathDebugging();
        RbelOptions.activateJexlDebugging();
        jwtMessage = extractMessage("rbelPath.curl");
        xmlMessage = extractMessage("xmlMessage.curl");
    }

    private RbelElement extractMessage(String fileName) throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/" + fileName);

        return RbelLogger.build().getRbelConverter()
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    }

    @Test
    public void assertThatPathValueFollowsConvention() {
        assertThat(jwtMessage.findNodePath())
            .isEqualTo("");
        assertThat(jwtMessage.getFirst("header").get().findNodePath())
            .isEqualTo("header");
        assertThat(jwtMessage.getFirst("header").get().getChildNodes().get(0).findNodePath())
            .startsWith("header.");
    }

    @Test
    public void simpleRbelPath_shouldFindTarget() {
        assertThat(jwtMessage.findElement("$.header"))
            .get()
            .isSameAs(jwtMessage.getFacetOrFail(RbelHttpMessageFacet.class).getHeader());

        assertThat(jwtMessage.findRbelPathMembers("$.body.body.nbf"))
            .containsExactly(jwtMessage.getFirst("body").get()
                .getFirst("body").get()
                .getFirst("nbf").get()
                .getFirst("content").get());
    }

    @Test
    public void rbelPathEndingOnStringValue_shouldReturnNestedValue() {
        assertThat(jwtMessage.findRbelPathMembers("$.body.body.sso_endpoint")
            .get(0).getRawStringContent())
            .startsWith("http://");
    }

    @Test
    public void squareBracketRbelPath_shouldFindTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$.['body'].['body'].['nbf']"))
            .containsExactly(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
    }

    @Test
    public void wildcardDescent_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$.body.[*].nbf"))
            .containsExactly(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
    }

    @Test
    public void recursiveDescent_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$..nbf"))
            .hasSize(2)
            .contains(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
        assertThat(jwtMessage.findRbelPathMembers("$.body..nbf"))
            .hasSize(1)
            .contains(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
    }

    @Test
    public void jexlExpression_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$..[?(key=='nbf')]"))
            .hasSize(2)
            .contains(jwtMessage.getFirst("body").get()
                .getFirst("body").get()
                .getFirst("nbf").get()
                .getFirst("content").get());
    }

    @Test
    public void complexJexlExpression_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$..[?(path=~'.*scopes_supported\\.\\d')]"))
            .hasSize(2);

        assertThat(jwtMessage.findRbelPathMembers("$.body.body..[?(path=~'.*scopes_supported\\.\\d')]"))
            .hasSize(2);
    }

    @Test
    public void findAllMembers() throws IOException {
        assertThat(jwtMessage.findRbelPathMembers("$..*"))
            .hasSize(173);

        FileUtils.writeStringToFile(new File("target/jsonNested.html"),
            RbelHtmlRenderer.render(List.of(jwtMessage)));

    }

    @Test
    public void findSingleElement_present() {
        assertThat(jwtMessage.findElement("$.body.body.authorization_endpoint"))
            .isPresent()
            .get()
            .isEqualTo(jwtMessage.findRbelPathMembers("$.body.body.authorization_endpoint").get(0));
    }

    @Test
    public void findSingleElement_notPresent_expectEmpty() {
        assertThat(jwtMessage.findElement("$.hfd7a89vufd"))
            .isEmpty();
    }

    @Test
    public void findSingleElementWithMultipleReturns_expectException() {
        assertThatThrownBy(() -> jwtMessage.findElement("$..*"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void eliminateContentInRbelPathResult() throws IOException {
        final String challengeMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/getChallenge.curl");

        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .parseMessage(challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(convertedMessage.findElement("$.body.challenge.signature").get())
            .isSameAs(convertedMessage.findElement("$.body.challenge.content.signature").get());
    }

    @Test
    public void successfulRequest_expectOnlyInitialTree() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.header.kid");

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getMessage)
            .filter(str -> str.startsWith("Resolving key")))
            .hasSize(3);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(str -> str.startsWith("Returning 1 result elements")))
            .hasSize(1);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(str -> str.contains("discSig")))
            .hasSize(1);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(str -> str.contains("$.body.header.kid")))
            .hasSize(2);
    }

    @Test
    public void successfulLongerRequest_treeSizeShouldBeAccordingly() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.body.acr_values_supported.0.content");

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .map(str -> str.split("http://localhost:8080/idpEnc/jwks.json").length - 1)
            .sorted(Comparator.reverseOrder())
            .findFirst().get())
            .isEqualTo(3);
    }

    @Test
    public void unsuccessfullyRequest_expectTreeOfLastSuccessfulPosition() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.body.acr_values_supported.content");

        listAppender.list.stream()
            .forEach(System.out::println);

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getMessage)
            .filter(str -> str.startsWith("No more candidate-nodes in RbelPath execution!")))
            .hasSize(1);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .map(str -> str.split("\\[0m\\n\\n").length)
            .sorted(Comparator.reverseOrder())
            .findFirst().get())
            .isEqualTo(1);
    }

    @Test
    public void unsuccessfulRequestWithAmbiguousFinalPosition_expectTreeOfAllCandidates() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.body.*.foobar");

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getMessage)
            .filter(str -> str.startsWith("No more candidate-nodes in RbelPath execution!")))
            .hasSize(1);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .map(str -> str.split("\\n\\n").length)
            .sorted(Comparator.reverseOrder())
            .findFirst().get())
            .isEqualTo(34);
    }

    @Test
    public void rbelPathWithReasonPhrase_shouldReturnTheValue() {
        assertThat(jwtMessage.findRbelPathMembers("$.reasonPhrase")
            .get(0).getRawStringContent())
            .isEqualTo("OK");
    }

    @ParameterizedTest
    @CsvSource({
        "$..[?(@.alg=='BP256R1')],$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header",
        "$..[?(@.hier=='ist kein text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
        "$..RegistryError.[?(@.hier=='ist kein text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
        "$..textTest[?(@.hier=='ist kein text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
        "$..RegistryError[1].textTest[?(@.hier=='ist kein text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text"
    })
    public void rbelPathWithAddSign_ShouldFindCorrectNode(String path1, String path2) {
        final List<RbelElement> path1Results = xmlMessage.findRbelPathMembers(path1);
        final List<RbelElement> path2Results = xmlMessage.findRbelPathMembers(path2);
        assertThat(path1Results)
            .containsAll(path2Results);

        assertThat(path2Results)
            .containsAll(path1Results);
    }

    private ListAppender<ILoggingEvent> listFollowingLoggingEventsForClass(Class<RbelPathExecutor> clazz) {
        Logger fooLogger = (Logger) LoggerFactory.getLogger(clazz);
        final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        fooLogger.addAppender(listAppender);
        return listAppender;
    }
}
