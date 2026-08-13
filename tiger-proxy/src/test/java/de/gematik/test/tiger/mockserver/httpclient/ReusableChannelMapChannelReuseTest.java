/*
 *  Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.mockserver.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.gematik.test.tiger.mockserver.httpclient.ReusableChannelMap.ChannelId;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/**
 * Regression test for TGR-1930: channel reuse broken because ChannelId lookup used
 * getRemoteServerAddress() while channel registration used retrieveActualRemoteAddress(). When
 * remoteServerAddress is null but is resolvable via outgoingChannel, lookups always missed, forcing
 * a new TCP connection on every request.
 */
class ReusableChannelMapChannelReuseTest {

  @Test
  void channelShouldBeReusedWhenRemoteAddressIsResolvedViaOutgoingChannel() {
    var remoteAddress = new InetSocketAddress("localhost", 8080);
    var incomingChannel = mockChannel();

    // Simulate the outgoingChannel providing the remote address (remoteServerAddress is null)
    var outgoingChannel = mockChannel();
    when(outgoingChannel.remoteAddress()).thenReturn(remoteAddress);

    // First request: remoteServerAddress is set explicitly (as in channel creation path)
    var firstRequest = new HttpRequestInfo(incomingChannel, null, remoteAddress);

    var channelFuture = mock(ChannelFuture.class);
    var completedChannel = mockCompletedChannel();
    when(channelFuture.channel()).thenReturn(completedChannel);

    var channelMap = new ReusableChannelMap();
    channelMap.addChannel(ChannelId.from(firstRequest), channelFuture);

    // Second request: remoteServerAddress is null, but outgoingChannel has the address.
    // This is the scenario that broke channel reuse before the fix.
    var secondRequest =
        HttpRequestInfo.builder()
            .incomingChannel(incomingChannel)
            .remoteServerAddress(null)
            .outgoingChannel(outgoingChannel)
            .build();

    var reusedChannel = channelMap.getChannelToReuse(secondRequest);

    assertThat(reusedChannel)
        .as("Channel should be reused when remote address is resolved via outgoingChannel.")
        .isNotNull();
  }

  @SuppressWarnings("unchecked")
  private Channel mockChannel() {
    var channel = mock(Channel.class);
    var attr = (Attribute<Object>) mock(Attribute.class);
    when(channel.attr(any())).thenReturn(attr);
    when(attr.get()).thenReturn(null);
    // Mock the channel ID for proper equality in ChannelId
    var channelId = mock(io.netty.channel.ChannelId.class);
    when(channel.id()).thenReturn(channelId);
    when(channel.isActive()).thenReturn(true);
    return channel;
  }

  private Channel mockCompletedChannel() {
    // RESPONSE_FUTURE is null → IS_RESPONSE_DONE returns TRUE → canBeReused() = true
    return mockChannel();
  }
}
