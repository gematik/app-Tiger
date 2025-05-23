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
package de.gematik.test.tiger.lib.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TestRbelMessageProvider {

  @Test
  void testTriggerNewReceivedMessageOK() {
    RbelMessageProvider rmProvider = new RbelMessageProvider();
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage"));
    assertThat(rmProvider.getMessages().get(0).getRawStringContent()).contains("TestMessage");
  }

  @Test
  void testTriggerNewReceivedMessageTwoOK() {
    RbelMessageProvider rmProvider = new RbelMessageProvider();
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
    assertThat(rmProvider.getMessages()).hasSize(2);
    assertThat(rmProvider.getMessages().get(0).getRawStringContent()).contains("TestMessage1");
    assertThat(rmProvider.getMessages().get(1).getRawStringContent()).contains("TestMessage2");
  }

  @Test
  void testWaitForMessageOK() {
    final RbelMessageProvider rmProvider = new RbelMessageProvider();
    // first add one dummy msg to list
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
    List<RbelElement> msgs = rmProvider.getMessages();
    assertThat(msgs).hasSize(1);

    // remember timestamp and create a task 1 second in the future
    // to trigger a second message
    long startms = System.currentTimeMillis();
    FutureTask<Void> futureTask =
        new FutureTask<>(
            () -> {
              rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
              return null;
            });
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    executorService.schedule(futureTask, 1, TimeUnit.SECONDS);
    executorService.shutdown();

    rmProvider.waitForMessage();

    // at least 1 second must have passed
    assertThat(System.currentTimeMillis() - startms).isGreaterThan(1000);
    msgs = rmProvider.getMessages();
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getRawStringContent()).contains("TestMessage1");
    assertThat(msgs.get(1).getRawStringContent()).contains("TestMessage2");
  }

  @Test
  void testWaitForMessageStartStepOK() {
    final RbelMessageProvider rmProvider = new RbelMessageProvider();
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
    List<RbelElement> msgs = rmProvider.getMessages();
    assertThat(msgs).hasSize(1);

    long startms = System.currentTimeMillis();
    FutureTask<Void> futureTask =
        new FutureTask<>(
            () -> {
              rmProvider.clearMessageQueue();
              return null;
            });
    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    executorService.schedule(futureTask, 1, TimeUnit.SECONDS);
    executorService.shutdown();

    rmProvider.waitForMessage();
    assertThat(System.currentTimeMillis() - startms).isGreaterThan(1000);
    msgs = rmProvider.getMessages();
    assertThat(msgs).isEmpty();
  }

  @Test
  void testStartStepOK() {
    final RbelMessageProvider rmProvider = new RbelMessageProvider();
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));

    List<RbelElement> msgs = rmProvider.getMessages();
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).getRawStringContent()).contains("TestMessage1");
    assertThat(msgs.get(1).getRawStringContent()).contains("TestMessage2");

    rmProvider.clearMessageQueue();
    msgs = rmProvider.getMessages();
    assertThat(msgs).isEmpty();
  }

  @Test
  // TODO what does this test
  void jexlToolboxTest() {
    final RbelMessageProvider rmProvider = new RbelMessageProvider();
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage1"));
    rmProvider.triggerNewReceivedMessage(buildMessageWithContent("TestMessage2"));
  }

  private RbelElement buildMessageWithContent(String messageBody) {
    return RbelElement.builder().rawContent(messageBody.getBytes(StandardCharsets.UTF_8)).build();
  }
}
