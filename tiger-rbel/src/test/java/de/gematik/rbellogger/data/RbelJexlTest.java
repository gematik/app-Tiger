/*
 * Copyright (c) 2023 gematik GmbH
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

import static de.gematik.rbellogger.TestUtils.localhostWithPort;
import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelJexlTest {

    private RbelElement response;
    private RbelElement request;

    @BeforeAll
    public static void init() {
        TigerJexlExecutor.executorSupplier = RbelJexlExecutor::new;
    }

    @BeforeEach
    public void setUp() throws IOException {
        final RbelLogger rbelLogger = RbelLogger.build();
        request = rbelLogger.getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/getRequest.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));
        response = rbelLogger.getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/rbelPath.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));
    }

    @Test
    void checkRequestMapElements() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "isRequest", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "isResponse == false", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "request == message", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkResponseStatusCode() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "response.statusCode == 200", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "response.statusCode == 400", Optional.empty()))
            .isFalse();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            response, "response.statusCode == 200", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "request.statusCode == null", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            response, "request.statusCode == null", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkResponseMapElements() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            response, "isRequest == false", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            response, "isResponse", Optional.empty()))
            .isTrue();
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            response, "request != message", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkJexlParsingForDoubleHeaders() throws IOException {
        RbelElement doubleHeaderMessage = RbelLogger.build().getRbelConverter()
            .parseMessage(readCurlFromFileWithCorrectedLineBreaks
                ("src/test/resources/sampleMessages/doubleHeader.curl").getBytes(), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            doubleHeaderMessage, "isResponse", Optional.empty()))
            .isTrue();
    }

    @Test
    void shouldFindReceiverPort() throws IOException {
        RbelElement request = RbelLogger.build().getRbelConverter().parseMessage(
            readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/getRequest.curl").getBytes(),
            localhostWithPort(44444), localhostWithPort(5432), Optional.of(ZonedDateTime.now()));

        assertThat(TigerJexlExecutor.matchesAsJexlExpression(
            request, "$.receiver.port == '5432'", Optional.empty()))
            .isTrue();
    }

    @Test
    void checkMatchTextExpression() {
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "localhost"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            response, "nbf"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "Keep-Alive"))
            .isTrue();
    }

    @Test
    void checkMatchRegexExpression() {
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "\\w+host"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "[a-zA-Z0-9_]+hos"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            response, ".*nbf"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "Keep[-/_]Alive"))
            .isTrue();
        assertThat(RbelJexlExecutor.matchAsTextExpression(
            request, "Keep-[a-zA-Z0-9_]+Alive"))
            .isFalse();
    }

    @Test
    void testValueFacetComparison() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(response, "$..signature.isValid == 'true'"))
            .isTrue();
    }

    @Test
    void testRbelEscaping() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(response, "$.body.header=~'.*discSig.*'"))
            .isTrue();
    }

    @Test
    void conditionalDescent() {
        assertThat(TigerJexlExecutor.matchesAsJexlExpression(response, "$.body.header =~ '.*discSig.*'"))
            .isTrue();
    }

    @Test
    void tokenSubstituteHelperRbelPathExtension() {
        assertThat(TokenSubstituteHelper.substitute("?{$.body.header}", null,
            Optional.of(new TigerJexlContext().withRootElement(response))))
            .contains("discSig");
    }
}
