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
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

@Slf4j
public class TestDirectForwardProxyWithRaceCondition {

  @SneakyThrows
  @Test
  public void cetpReplayWithRandomTcpChunks() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/stapelsignatur_log.pcapng", 50294, 7001, false)
            .readReplay();
    val tigerProxy = replayer.replayWithDirectForwardUsing(new TigerProxyConfiguration());

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/cetpReplay.html").toPath(), html.getBytes());
    tigerProxy.waitForAllCurrentMessagesToBeParsed();

    waitForMessages(tigerProxy, 68);
  }

  @SneakyThrows
  @Test
  public void testSicctHandshake() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/sicctHandshakeDecryptedPcap.json", 53406, 4741, false)
            .readReplay();
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("sicct")));

    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    assertThat(tigerProxy.getRbelMessagesList()).hasSize(14);
  }

  @SneakyThrows
  @Test
  public void httpReplayWithRandomTcpChunks() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/stapelsignatur_log.pcapng", 53335, 80, false)
            .readReplay();
    val tigerProxy = replayer.replayWithDirectForwardUsing(new TigerProxyConfiguration());

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pcapReplayHttp.html").toPath(), html.getBytes());
    var messages =
        tigerProxy.getRbelMessagesList().stream()
            .filter(
                msg ->
                    msg.hasFacet(RbelHttpRequestFacet.class)
                        || msg.hasFacet(RbelHttpResponseFacet.class))
            .toList();

    assertThat(messages).hasSize(16);
    assertThat(messages.get(0)).extractChildWithPath("$.body").getContent().hasSize(0);
    assertThat(messages.get(1)).extractChildWithPath("$.body").getContent().hasSize(14597);
  }

  private static void waitForMessages(TigerProxy tigerProxy, int expectedMessages) {
    try {
      Awaitility.await()
          .atMost(2, TimeUnit.SECONDS)
          .until(() -> tigerProxy.getRbelMessagesList().size(), i -> i == expectedMessages);
    } catch (ConditionTimeoutException ex) {
      tigerProxy.getRbelMessagesList().stream()
          .map(RbelElement::printTreeStructure)
          .forEach(System.out::println);
      throw ex;
    }
  }
}
