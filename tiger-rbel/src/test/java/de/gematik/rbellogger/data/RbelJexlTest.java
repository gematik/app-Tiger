/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.localhostWithPort;
import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelJexlTest {

    private RbelElement response;
    private RbelElement request;
    private RbelJexlExecutor jexlExecutor;

    @BeforeEach
    public void setUp() throws IOException {
        RbelOptions.activateJexlDebugging();

        final RbelLogger rbelLogger = RbelLogger.build();
        request = rbelLogger.getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/getRequest.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));
        response = rbelLogger.getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/rbelPath.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));
        jexlExecutor = new RbelJexlExecutor();
    }

    @Test
    void checkRequestMapElements() {
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "isRequest", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "isResponse == false", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "request == message", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkResponseStatusCode() {
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "response.statusCode == 200", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "response.statusCode == 400", Optional.empty()))
            .isFalse();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            response, "response.statusCode == 200", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "request.statusCode == null", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            response, "request.statusCode == null", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkResponseMapElements() {
        assertThat(jexlExecutor.matchesAsJexlExpression(
            response, "isRequest == false", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            response, "isResponse", Optional.empty()))
            .isTrue();
        assertThat(jexlExecutor.matchesAsJexlExpression(
            response, "request != message", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkJexlParsingForDoubleHeaders() throws IOException {
        RbelElement doubleHeaderMessage = RbelLogger.build().getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/doubleHeader.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(jexlExecutor.matchesAsJexlExpression(
            doubleHeaderMessage, "isResponse", Optional.empty()))
            .isTrue();
    }

    @Test
    void shouldFindReceiverPort() throws IOException {
        RbelElement request = RbelLogger.build().getRbelConverter().parseMessage(
            readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/getRequest.curl").getBytes(),
            localhostWithPort(44444), localhostWithPort(5432), Optional.of(ZonedDateTime.now()));

        assertThat(jexlExecutor.matchesAsJexlExpression(
            request, "$.receiver.port == '5432'", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkMatchTextExpression() {
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "localhost"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            response, "nbf"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "Keep-Alive"))
            .isTrue();
    }

    @Test
    void checkMatchRegexExpression() {
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "\\w+host"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "[a-zA-Z0-9_]+hos"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            response, ".*nbf"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "Keep[-/_]Alive"))
            .isTrue();
        assertThat(jexlExecutor.matchAsTextExpression(
            request, "Keep-[a-zA-Z0-9_]+Alive"))
            .isFalse();
    }

    @Test
    void testValueFacetComparison() {
        assertThat(jexlExecutor.matchesAsJexlExpression(response, "$..signature.isValid == 'true'"))
            .isTrue();
    }

    @Test
    void testRbelEscaping() {
        assertThat(jexlExecutor.matchesAsJexlExpression(response, "$.body.header=~'.*discSig.*'"))
            .isTrue();
    }

    @Test
    void conditionalDescent() {
        assertThat(jexlExecutor.matchesAsJexlExpression(response, "$.body.header =~ '.*discSig.*'"))
            .isTrue();
    }

    @Test
    void tokenSubstituteHelperRbelPathExtension() {
        RbelJexlExecutor.ELEMENT_STACK.push(response);
        try {
            assertThat(TokenSubstituteHelper.substitute("?{$.body.header}", null))
                .contains("discSig");
        } finally {
            RbelJexlExecutor.ELEMENT_STACK.removeFirstOccurrence(response);
        }
    }
}
