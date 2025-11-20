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

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.EmbeddedHttpbin;
import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyModifierDescription;
import de.gematik.test.tiger.proxy.handler.RbelBinaryModifierPlugin;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import kong.unirest.core.Unirest;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@Slf4j
class TestTigerProxyBinaryModification {

  private static final String A_SAMPLE_HTTP_EXCHANGE =
      """
      C: GET / HTTP/1.1
      C: Host: example.com
      C:
      S: HTTP/1.1 200 OK
      S: Server: gws
      S: Content-Length: 0
      S:
      C: GET /foo HTTP/1.1
      C: Host: example.com
      C:
      S: HTTP/1.1 201 OK
      S: Server: fdsa
      S: Content-Length: 0
      S:
      """;

  @SneakyThrows
  @RepeatedTest(
      5) // to test the chunking logic (java can not guarantee the segmentation of the message)
  public void modifyDirectHttpTrafficWithFragmentedChunking() {
    val replayer = PcapReplayer.writeReplay(A_SAMPLE_HTTP_EXCHANGE);
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration()
                .setDirectReverseProxy(
                    DirectReverseProxyInfo.builder()
                        .hostname("localhost")
                        .port(80)
                        .modifierPlugins(
                            List.of(
                                TigerProxyModifierDescription.builder()
                                    .name("MyBinaryModifier")
                                    .parameters(
                                        Map.of(
                                            "targetString",
                                            "GET /foo",
                                            "replacementString",
                                            "GET /foobar"))
                                    .build(),
                                TigerProxyModifierDescription.builder()
                                    .name("MyBinaryModifier")
                                    .parameters(
                                        Map.of(
                                            "targetString",
                                            "Server: fdsa",
                                            "replacementString",
                                            "Server: blubblab"))
                                    .build(),
                                TigerProxyModifierDescription.builder()
                                    .name("MyBinaryModifier")
                                    .parameters(
                                        Map.of(
                                            "targetString",
                                            "HTTP/1.1 201 OK",
                                            "replacementString",
                                            "HTTP/1.1 202"))
                                    .build()))
                        .build()));
    tigerProxy.waitForAllCurrentMessagesToBeParsed();

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pcapReplayerHttp.html").toPath(), html.getBytes());
    TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
        tigerProxy, 4, 2);

    assertThat(tigerProxy.getRbelMessagesList().get(2))
        .andPrintTree()
        .extractChildWithPath("$.path")
        .asString()
        .endsWith("/foobar");
    assertThat(tigerProxy.getRbelMessagesList().get(3))
        .andPrintTree()
        .hasStringContentEqualToAtPosition("$.responseCode", "202")
        .hasStringContentEqualToAtPosition("$.header.[~'server']", "blubblab");
  }

  @SneakyThrows
  @Test
  public void lotsOfHttpMessages() {
    EmbeddedHttpbin httpbin = new EmbeddedHttpbin(0, true);
    httpbin.start();
    try (val tigerProxy =
            new TigerProxy(
                new TigerProxyConfiguration()
                    .setDirectReverseProxy(
                        DirectReverseProxyInfo.builder()
                            .hostname("localhost")
                            .port(httpbin.getPort())
                            .modifierPlugins(
                                List.of(
                                    TigerProxyModifierDescription.builder()
                                        .name("MyBinaryModifier")
                                        .parameters(
                                            Map.of(
                                                "targetString",
                                                "GET /foo",
                                                "replacementString",
                                                "GET /foobar"))
                                        .build()))
                            .build()));
        val unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().connectTimeout(5000).requestTimeout(5000);
      val numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
        unirestInstance.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo" + i).asEmpty();
      }

      Awaitility.await()
          .atMost(10, TimeUnit.SECONDS)
          .until(() -> tigerProxy.getRbelMessagesList().size() == numMessages * 2);
      tigerProxy.waitForAllCurrentMessagesToBeParsed();

      Assertions.assertThat(tigerProxy.getRbelMessagesList()).hasSize(numMessages * 2);

      final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
      Files.write(new File("target/pcapReplayerHttp.html").toPath(), html.getBytes());

      for (int i = 0; i < numMessages; i++) {
        assertThat(tigerProxy.getRbelMessagesList().get(i * 2))
            .andPrintTree()
            .extractChildWithPath("$.path")
            .asString()
            .endsWith("/foobar" + i);
      }
    } finally {
      httpbin.stop();
    }
  }

  @Data
  public static class MyBinaryModifier implements RbelBinaryModifierPlugin {
    private String targetString;
    private String replacementString;

    @Override
    public Optional<byte[]> modify(RbelElement target, RbelConverter converter) {
      if (target.getRawStringContent().contains(targetString)) {
        final String newContent =
            target.getRawStringContent().replace(targetString, replacementString);
        log.info("Modifying content from {} to {}", target.getRawStringContent(), newContent);
        return Optional.of(newContent.getBytes());
      } else {
        return Optional.empty();
      }
    }

    public String toString() {
      return "MyBinaryModifier{"
          + "targetString='"
          + targetString
          + '\''
          + ", replacementString='"
          + replacementString
          + '\''
          + '}';
    }
  }
}
