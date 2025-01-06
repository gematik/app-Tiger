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
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.data.RbelElement;
import java.io.IOException;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RbelPathExecutorLoggingTest {

  private RbelElement jwtMessage;

  @BeforeEach
  public void setUp() throws IOException {
    // needed to be able to parse the logging stream for corresponding messages expected in test
    // runs
    RbelOptions.activateRbelPathDebugging();
    jwtMessage = extractMessage();
  }

  @AfterAll
  public static void cleanup() {
    RbelOptions.deactivateRbelPathDebugging();
  }

  private RbelElement extractMessage() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/rbelPath.curl");

    return RbelLogger.build()
        .getRbelConverter()
        .convertElement(curlMessage.getBytes(), null);
  }

  @Test
  void successfulRequest_expectOnlyInitialTree() {
    final ListAppender<ILoggingEvent> listAppender =
        listFollowingLoggingEventsForClass(RbelPathExecutor.class);
    jwtMessage.findRbelPathMembers("$.body.header.kid");

    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getMessage)
                .filter(str -> str.startsWith("Resolving key")))
        .hasSize(3);
    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(str -> str.startsWith("Returning 1 result elements")))
        .hasSize(1);
    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(str -> str.contains("discSig")))
        .hasSize(1);
    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(str -> str.contains("$.body.header.kid")))
        .hasSize(3);
  }

  @Test
  void successfulLongerRequest_treeSizeShouldBeAccordingly() {
    final ListAppender<ILoggingEvent> listAppender =
        listFollowingLoggingEventsForClass(RbelPathExecutor.class);
    jwtMessage.findRbelPathMembers("$.body.body.acr_values_supported.0.content");

    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(str -> str.split("http://localhost:8080/idpEnc/jwks.json").length - 1)
                .max(Comparator.naturalOrder()))
        .get()
        .isEqualTo(3);
  }

  @Test
  void unsuccessfullyRequest_expectTreeOfLastSuccessfulPosition() {
    final ListAppender<ILoggingEvent> listAppender =
        listFollowingLoggingEventsForClass(RbelPathExecutor.class);
    jwtMessage.findRbelPathMembers("$.body.body.acr_values_supported.content");

    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getMessage)
                .filter(str -> str.startsWith("No more candidate-nodes in RbelPath execution!")))
        .hasSize(1);
    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(str -> str.split("\\[0m\\n\\n").length)
                .max(Comparator.naturalOrder()))
        .get()
        .isEqualTo(1);
  }

  @Test
  void unsuccessfulRequestWithAmbiguousFinalPosition_expectListOfAllCandidates() {
    final ListAppender<ILoggingEvent> listAppender =
        listFollowingLoggingEventsForClass(RbelPathExecutor.class);
    jwtMessage.findRbelPathMembers("$.body.body.*.foobar");

    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getMessage)
                .filter(str -> str.startsWith("No more candidate-nodes in RbelPath execution!")))
        .hasSize(1);
    assertThat(
            listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(str -> str.split(", ").length)
                .max(Comparator.naturalOrder()))
        .get()
        .isEqualTo(34);
  }

  private ListAppender<ILoggingEvent> listFollowingLoggingEventsForClass(
      Class<RbelPathExecutor> clazz) {
    Logger fooLogger = (Logger) LoggerFactory.getLogger(clazz);
    final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);
    return listAppender;
  }
}
