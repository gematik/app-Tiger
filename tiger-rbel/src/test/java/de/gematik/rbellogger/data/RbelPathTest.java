/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelPathTest {

    private RbelElement jwtMessage;
    private RbelElement xmlMessage;

    @BeforeEach
    public void setUp() throws IOException {
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
    void assertThatPathValueFollowsConvention() {
        assertThat(jwtMessage.findNodePath())
            .isEqualTo("");
        assertThat(jwtMessage.getFirst("header").get().findNodePath())
            .isEqualTo("header");
        assertThat(jwtMessage.getFirst("header").get().getChildNodes().get(0).findNodePath())
            .startsWith("header.");
    }

    @Test
    void simpleRbelPath_shouldFindTarget() {
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
    void rbelPathEndingOnStringValue_shouldReturnNestedValue() {
        assertThat(jwtMessage.findRbelPathMembers("$.body.body.sso_endpoint")
            .get(0).getRawStringContent())
            .startsWith("http://");
    }

    @Test
    void squareBracketRbelPath_shouldFindTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$.['body'].['body'].['nbf']"))
            .containsExactly(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
    }

    @Test
    void wildcardDescent_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$.body.[*].nbf"))
            .containsExactly(jwtMessage.getFirst("body").get().
                getFirst("body").get().
                getFirst("nbf").get().
                getFirst("content").get());
    }

    @Test
    void recursiveDescent_shouldFindSpecificTarget() {
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
    void jexlExpression_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$..[?(key=='nbf')]"))
            .hasSize(2)
            .contains(jwtMessage.getFirst("body").get()
                .getFirst("body").get()
                .getFirst("nbf").get()
                .getFirst("content").get());
    }

    @Test
    void complexJexlExpression_shouldFindSpecificTarget() {
        assertThat(jwtMessage.findRbelPathMembers("$..[?(path=~'.*scopes_supported\\.\\d')]"))
            .hasSize(2);

        assertThat(jwtMessage.findRbelPathMembers("$.body.body..[?(path=~'.*scopes_supported\\.\\d')]"))
            .hasSize(2);
    }

    @Test
    void findAllMembers() throws IOException {
        assertThat(jwtMessage.findRbelPathMembers("$..*"))
            .hasSizeGreaterThan(214);

        FileUtils.writeStringToFile(new File("target/jsonNested.html"),
            RbelHtmlRenderer.render(List.of(jwtMessage)));

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
        assertThat(jwtMessage.findElement("$.hfd7a89vufd"))
            .isEmpty();
    }

    @Test
    void findSingleElementWithMultipleReturns_expectException() {
        assertThatThrownBy(() -> jwtMessage.findElement("$..*"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void eliminateContentInRbelPathResult() throws IOException {
        final String challengeMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/getChallenge.curl");

        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .parseMessage(challengeMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(convertedMessage.findElement("$.body.challenge.signature").get())
            .isSameAs(convertedMessage.findElement("$.body.challenge.content.signature").get());
    }

    @Test
    void rbelPathWithReasonPhrase_shouldReturnTheValue() {
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
        "$..RegistryError[1].textTest[?(@.hier=='ist kein text')].text,$.body.RegistryResponse.RegistryErrorList.RegistryError.textTest.text",
        "$..x5c.0.content.0.7..[?(@.0 == '1.3.36.8.3.3')],$..7.content.1"
    })
    void rbelPathWithAddSign_ShouldFindCorrectNode(String path1, String path2) {
        final List<RbelElement> path1Results = xmlMessage.findRbelPathMembers(path1);
        final List<RbelElement> path2Results = xmlMessage.findRbelPathMembers(path2);
        assertThat(path1Results)
            .containsAll(path2Results);

        assertThat(path2Results)
            .containsAll(path1Results);
    }

    @Test
    void testRelativeJexlSelectorInJwt() {
        final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/nestedJwtTraffic.tgr")
            .build();
        final RbelLogger logger = RbelLogger.build(RbelConfiguration.builder()
            .capturer(fileReaderCapturer)
            .build());
        fileReaderCapturer.initialize();
        final RbelElement secondResponse = logger.getMessageList().get(3);

        RbelElementAssertion.assertThat(secondResponse)
            .extractChildWithPath("$.body.body.idp_entity.[?(@.iss.content=='https://idpsek.dev.gematik.solutions')]")
            .hasStringContentEqualTo("{\"iss\":\"https://idpsek.dev.gematik.solutions\",\"organization_name\":\"gematik\",\"logo_uri\":null,\"user_type_supported\":\"IP\"}");
    }

    @Test
    void testAbsoluteJexlSelectorInJwt() {
        final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/nestedJwtTraffic.tgr")
            .build();
        final RbelLogger logger = RbelLogger.build(RbelConfiguration.builder()
            .capturer(fileReaderCapturer)
            .build());
        fileReaderCapturer.initialize();
        final RbelElement secondResponse = logger.getMessageList().get(3);

        RbelElementAssertion.assertThat(secondResponse)
            .extractChildWithPath("$.body.body.idp_entity.[?(@.iss.content==$.body.body.idp_entity.0.iss.content)]")
            .hasStringContentEqualTo("{\"iss\":\"https://idpsek.dev.gematik.solutions\",\"organization_name\":\"gematik\",\"logo_uri\":null,\"user_type_supported\":\"IP\"}");
    }
}
