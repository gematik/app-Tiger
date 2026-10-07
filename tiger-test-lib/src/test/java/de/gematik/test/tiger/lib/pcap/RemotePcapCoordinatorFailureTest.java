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
package de.gematik.test.tiger.lib.pcap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("RemotePcapCoordinator when a remote proxy fails")
class RemotePcapCoordinatorFailureTest {

  private RemotePcapCoordinator coordinator;
  private FakeRemoteProxy remote;
  private Map<String, Duration> proxies;

  @BeforeEach
  void setUp() throws IOException {
    coordinator = new RemotePcapCoordinator();
    remote = new FakeRemoteProxy(new byte[0]);
    proxies = Map.of(remote.url(), Duration.ZERO);
  }

  @AfterEach
  void tearDown() {
    remote.close();
    coordinator.clear();
  }

  @Test
  @DisplayName("a proxy that refuses to capture, with a reason or without, has nothing to stop")
  void refusedStartLeavesNothingToStop() {
    remote.refuseStartsWith(403);
    coordinator.startRemoteCapture(proxies, "refused");
    assertThat(coordinator.stopRemoteCapture()).isEmpty();

    remote.refuseStartsWith(500);
    coordinator.startRemoteCapture(proxies, "broken");
    assertThat(coordinator.stopRemoteCapture()).isEmpty();

    assertThat(remote.startBodies()).hasSize(2);
    assertThat(remote.calls()).as("no capture, no calls about one").isEmpty();
  }

  @Test
  @DisplayName("suspend and resume that a proxy rejects are reported, not thrown")
  void rejectedSuspendAndResumeAreNotThrown() {
    coordinator.startRemoteCapture(proxies, "rejected");
    remote.refuseCaptureCallsWith(400);

    assertThatCode(
            () -> {
              coordinator.suspend();
              coordinator.resume();
            })
        .doesNotThrowAnyException();

    assertThat(remote.calls()).as("neither was carried out there").isEmpty();
  }

  @Test
  @DisplayName("what a proxy says went wrong with a suspend, resume or stop is logged")
  void proxyErrorsAreLogged() {
    Logger logger = (Logger) LoggerFactory.getLogger(RemotePcapCoordinator.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      coordinator.startRemoteCapture(proxies, "failing");
      remote.failCaptureCallsWith(500, "NullPointerException at Somewhere");

      coordinator.suspend();
      coordinator.stopRemoteCapture();

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .filteredOn(message -> message.contains("NullPointerException at Somewhere"))
          .hasSize(2);
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  @DisplayName("a stop that a proxy rejects yields no file to download")
  void rejectedStopYieldsNoFile() {
    coordinator.startRemoteCapture(proxies, "rejected");
    remote.refuseCaptureCallsWith(400);

    assertThat(coordinator.stopRemoteCapture()).isEmpty();
  }

  @Test
  @DisplayName("a proxy that has gone away is given up on at suspend, resume and stop")
  void vanishedProxyIsGivenUpOn() {
    coordinator.startRemoteCapture(proxies, "vanishing");
    remote.close();

    assertThatCode(
            () -> {
              coordinator.suspend();
              coordinator.resume();
            })
        .doesNotThrowAnyException();
    assertThat(coordinator.stopRemoteCapture()).isEmpty();
  }

  @Test
  @DisplayName("a scenario after one whose start was refused starts normally")
  void nextScenarioStartsCleanAfterAFailedOne() {
    remote.refuseStartsWith(500);
    coordinator.startRemoteCapture(proxies, "first");
    coordinator.stopRemoteCapture();
    remote.refuseStartsWith(200);

    coordinator.startRemoteCapture(proxies, "second");
    Map<String, RemotePcapMetadata> stopped = coordinator.stopRemoteCapture();

    assertThat(stopped).containsOnlyKeys(remote.url());
  }
}
