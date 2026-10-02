/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

/**
 * A single message can be converted by several plugins that each wait for a not-yet-finished
 * predecessor (TLS read/write, VAU, ASL, websocket, pairing, ...). All of those waits must share
 * one deadline for the whole message; otherwise a message needing N such waits can legitimately
 * take N times parsingTimeout to convert, while every outer caller (e.g.
 * MultipleBinaryConnectionParser.waitForAllParsingTasksToBeFinished) only grants a single
 * parsingTimeout before giving up on it.
 */
class RbelConversionExecutorDeadlineTest {

  @Test
  void pluginsWaitingOnTheSameStuckPredecessorShareOneDeadlineInsteadOfMultiplying() {
    var converter =
        RbelLogger.build(RbelConfiguration.builder().parsingTimeoutInSeconds(1).build())
            .getRbelConverter();
    addStuckPredecessor(converter);

    var waitCount = new AtomicInteger();
    converter.addConverter(waitingPlugin(waitCount));
    converter.addConverter(waitingPlugin(waitCount));

    var start = Instant.now();
    converter.convertElement("second message".getBytes(), null);
    var elapsed = Duration.between(start, Instant.now());

    assertThat(waitCount)
        .as("both plugins must actually have tried to wait, or the test proves nothing")
        .hasValue(2);
    assertThat(elapsed)
        .as(
            "two plugins each waiting on the same stuck predecessor must share the message's one"
                + " parsingTimeout budget, not pay it twice over")
        .isLessThan(Duration.ofMillis(1800));
  }

  private RbelConverterPlugin waitingPlugin(AtomicInteger waitCount) {
    return RbelConverterPlugin.createPlugin(
        (element, executor) -> {
          waitCount.incrementAndGet();
          try {
            executor.waitForAllElementsBeforeGivenToBeParsed(element);
          } catch (RuntimeException expected) {
            // the predecessor never finishes; every plugin gives up on it independently
          }
        });
  }

  private void addStuckPredecessor(RbelConverter converter) {
    var predecessor =
        new RbelElement(RandomStringUtils.insecure().nextAlphanumeric(16).getBytes(), null);
    predecessor.setConversionPhase(RbelConversionPhase.CONTENT_PARSING);
    converter.getHistory().addMessageToHistory(predecessor, ZonedDateTime.now());
  }
}
