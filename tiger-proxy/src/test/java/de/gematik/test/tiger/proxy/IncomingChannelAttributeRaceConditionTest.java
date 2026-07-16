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

import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler;
import de.gematik.test.tiger.mockserver.httpclient.ClientBootstrapFactory;
import de.gematik.test.tiger.mockserver.httpclient.HttpClientInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.Attribute;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for regression of the INCOMING_CHANNEL attribute race condition.
 *
 * <p>The bug occurred when the server sent data immediately upon connection, before the
 * ChannelFutureListener callback had a chance to set the INCOMING_CHANNEL attribute. This caused
 * IllegalStateException: "Incoming channel is not set" in BinaryBridgeHandler.channelRead0().
 *
 * <p>The fix was to set the INCOMING_CHANNEL attribute via Bootstrap.attr() before connecting,
 * rather than in a listener callback after connection success.
 */
@Slf4j
@ResetTigerConfiguration
class IncomingChannelAttributeRaceConditionTest {

  private static final String IMMEDIATE_SERVER_MESSAGE = "HELLO FROM SERVER\n";
  private static final String CLIENT_MESSAGE = "HELLO FROM CLIENT\n";

  /**
   * Deterministic unit test that verifies the INCOMING_CHANNEL attribute is set via
   * Bootstrap.attr() before the channel connects. This is the key fix for the race condition -
   * setting the attribute in the bootstrap configuration ensures it's available immediately when
   * the channel becomes active, rather than waiting for a ChannelFutureListener callback.
   */
  @Test
  @DisplayName("INCOMING_CHANNEL attribute must be set in Bootstrap before connection")
  void incomingChannelAttribute_shouldBeSetViaBootstrapBeforeConnection() throws Exception {
    // Setup mocks
    MockServerConfiguration mockConfig = mock(MockServerConfiguration.class);
    when(mockConfig.socketConnectionTimeoutInMillis()).thenReturn(5000L);
    when(mockConfig.tcpIdleTimeoutInMillis()).thenReturn(1000);

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    try {
      ClientBootstrapFactory factory = new ClientBootstrapFactory(mockConfig, eventLoopGroup);

      // Create a mock incoming channel with necessary attributes
      Channel mockIncomingChannel = mock(Channel.class);
      @SuppressWarnings("unchecked")
      Attribute<Integer> loopCounterAttr = mock(Attribute.class);
      @SuppressWarnings("unchecked")
      Attribute<Channel> outgoingChannelAttr = mock(Attribute.class);

      when(mockIncomingChannel.hasAttr(ClientBootstrapFactory.LOOP_COUNTER)).thenReturn(false);
      when(mockIncomingChannel.attr(ClientBootstrapFactory.LOOP_COUNTER))
          .thenReturn(loopCounterAttr);
      // Mock the OUTGOING_CHANNEL attribute that gets set in registerNewChannel
      when(mockIncomingChannel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL))
          .thenReturn(outgoingChannelAttr);

      // Create a mock HttpClientInitializer
      HttpClientInitializer mockInitializer = mock(HttpClientInitializer.class);
      when(mockInitializer.usesForwardProxy()).thenReturn(false);

      // Use a test server to actually connect and verify the attribute
      try (ServerSocket testServer = new ServerSocket(0)) {
        int port = testServer.getLocalPort();

        // Create channel using the factory
        ChannelFuture channelFuture =
            factory
                .configureChannel()
                .isSecure(false)
                .incomingChannel(mockIncomingChannel)
                .remoteAddress(new InetSocketAddress("localhost", port))
                .clientInitializer(mockInitializer)
                .errorIfChannelClosedWithoutResponse(false)
                .connectToChannel();

        // Wait for connection (or timeout - we don't care about success, just the attribute)
        channelFuture.awaitUninterruptibly(2, TimeUnit.SECONDS);

        // The critical assertion: INCOMING_CHANNEL must be set on the channel
        // This proves the attribute was set via Bootstrap.attr() before connection,
        // not in a listener callback after connection
        Channel outgoingChannel = channelFuture.channel();
        assertThat(outgoingChannel.attr(INCOMING_CHANNEL).get())
            .as("INCOMING_CHANNEL attribute must be set before channel becomes active")
            .isNotNull()
            .isSameAs(mockIncomingChannel);

        // Cleanup
        outgoingChannel.close().awaitUninterruptibly();
      }
    } finally {
      eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
    }
  }
}
