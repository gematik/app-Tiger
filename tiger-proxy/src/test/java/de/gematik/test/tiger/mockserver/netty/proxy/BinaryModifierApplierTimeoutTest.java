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
package de.gematik.test.tiger.mockserver.netty.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyException;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import de.gematik.test.tiger.proxy.handler.RbelBinaryModifierPlugin;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BinaryModifierApplierTimeoutTest {

  private final ExecutorService jammed = Executors.newSingleThreadExecutor();
  private final CountDownLatch release = new CountDownLatch(1);

  @AfterEach
  void tearDown() {
    release.countDown();
    jammed.shutdownNow();
  }

  @Test
  @DisplayName("Waiting for a message before modifying it is bound by the configured timeout")
  void aStuckMessageDoesNotBlockTheModifierForEver() {
    jamTheOnlyThread();
    var applier = new BinaryModifierApplier(configurationWithParsingTimeoutOf(1), jammed);
    var message = BinaryMessage.bytes(request());
    var ctx = channelContext();

    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () ->
            assertThatThrownBy(
                    () -> applier.applyModifierPlugins(message, ctx, RbelMessageKind.REQUEST))
                .as("without a timeout the netty thread hangs on whatever is stuck in the parser")
                .isInstanceOf(TigerProxyException.class)
                .hasMessageContaining("Gave up after 1 seconds"));
  }

  private MockServerConfiguration configurationWithParsingTimeoutOf(int timeoutInSeconds) {
    var tigerProxy = mock(TigerProxy.class);
    when(tigerProxy.getTigerProxyConfiguration())
        .thenReturn(
            TigerProxyConfiguration.builder().parsingTimeoutInSeconds(timeoutInSeconds).build());
    var binaryProxyListener = mock(BinaryExchangeHandler.class);
    when(binaryProxyListener.getTigerProxy()).thenReturn(tigerProxy);
    return MockServerConfiguration.configuration()
        .rbelConverter(RbelLogger.build().getRbelConverter())
        .binaryProxyListener(binaryProxyListener)
        .binaryModifierPlugins(List.of(leaveTheMessageAlone()));
  }

  private static RbelBinaryModifierPlugin leaveTheMessageAlone() {
    return (target, rbelConverter) -> Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private static ChannelHandlerContext channelContext() {
    var channel = mock(Channel.class);
    doReturn(mock(Attribute.class)).when(channel).attr(any());
    when(channel.remoteAddress()).thenReturn(new InetSocketAddress("localhost", 4711));
    var ctx = mock(ChannelHandlerContext.class);
    when(ctx.channel()).thenReturn(channel);
    return ctx;
  }

  @SneakyThrows
  private void jamTheOnlyThread() {
    var occupied = new CountDownLatch(1);
    jammed.submit(
        () -> {
          occupied.countDown();
          release.await();
          return null;
        });
    occupied.await();
  }

  private static byte[] request() {
    return "GET / HTTP/1.1\r\nHost: server\r\nContent-Length: 0\r\n\r\n"
        .getBytes(StandardCharsets.UTF_8);
  }
}
