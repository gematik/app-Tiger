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

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
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

class RbelPathLoggingTest {

    private RbelElement jwtMessage;
    private RbelElement xmlMessage;

    @BeforeEach
    public void setUp() throws IOException {
        RbelOptions.activateRbelPathDebugging();
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
    void successfulRequest_expectOnlyInitialTree() {
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
    void successfulLongerRequest_treeSizeShouldBeAccordingly() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.body.acr_values_supported.0.content");

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .map(str -> str.split("http://localhost:8080/idpEnc/jwks.json").length - 1)
            .max(Comparator.naturalOrder()))
            .get()
            .isEqualTo(3);
    }

    @Test
    void unsuccessfullyRequest_expectTreeOfLastSuccessfulPosition() {
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
            .max(Comparator.naturalOrder()))
            .get()
            .isEqualTo(1);
    }

    @Test
    void unsuccessfulRequestWithAmbiguousFinalPosition_expectTreeOfAllCandidates() {
        final ListAppender<ILoggingEvent> listAppender = listFollowingLoggingEventsForClass(RbelPathExecutor.class);
        jwtMessage.findRbelPathMembers("$.body.body.*.foobar");

        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getMessage)
            .filter(str -> str.startsWith("No more candidate-nodes in RbelPath execution!")))
            .hasSize(1);
        assertThat(listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .map(str -> str.split("\\n\\n").length)
            .max(Comparator.naturalOrder()))
            .get()
            .isEqualTo(34);
    }

    private ListAppender<ILoggingEvent> listFollowingLoggingEventsForClass(Class<RbelPathExecutor> clazz) {
        Logger fooLogger = (Logger) LoggerFactory.getLogger(clazz);
        final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        fooLogger.addAppender(listAppender);
        return listAppender;
    }
}
