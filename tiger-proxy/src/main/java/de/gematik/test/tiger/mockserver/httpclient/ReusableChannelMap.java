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
package de.gematik.test.tiger.mockserver.httpclient;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.val;

public class ReusableChannelMap {
  private final Multimap<ChannelId, ReusableChannel> channelMap =
      Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
  private static final long DEFAULT_CHANNEL_POOL_TTL_MILLIS = 5L * 60 * 1000;

  public synchronized ChannelFuture getChannelToReuse(RequestInfo<?> requestInfo) {
    return getChannelToReuse(requestInfo, false);
  }

  /**
   * @param requireBoundChannel if true, only a channel bound to the requesting incoming channel is
   *     eligible (see {@link ReusableChannel#canBeReusedBy(Channel, boolean)}).
   */
  public synchronized ChannelFuture getChannelToReuse(
      RequestInfo<?> requestInfo, boolean requireBoundChannel) {
    val channelId = ChannelId.from(requestInfo);
    val pooledChannels = channelMap.get(channelId);
    val requestingIncoming = requestInfo.getIncomingChannel();

    val reuseableChannel =
        pooledChannels.stream()
            .filter(rc -> rc.canBeReusedBy(requestingIncoming, requireBoundChannel))
            .findFirst()
            .orElse(null);

    if (reuseableChannel != null) {
      reuseableChannel.markAsUsed();
      // Re-insert at end to maintain sorted order (oldest first)
      channelMap.remove(channelId, reuseableChannel);
      channelMap.put(channelId, reuseableChannel);
      return reuseableChannel.getFutureOutgoingChannel();
    }
    return null;
  }

  /** Periodically clean up expired channels from the pool. */
  public synchronized void cleanupExpiredChannels() {
    var expired = new ArrayList<Map.Entry<ChannelId, ReusableChannel>>();
    for (ChannelId key : channelMap.keySet()) {
      channelMap.get(key).stream()
          .takeWhile(channel -> channel.isExpired(DEFAULT_CHANNEL_POOL_TTL_MILLIS))
          .forEach(channel -> expired.add(new AbstractMap.SimpleEntry<>(key, channel)));
    }
    removeChannels(expired);
  }

  private void removeChannels(List<Map.Entry<ChannelId, ReusableChannel>> toRemove) {
    toRemove.forEach(
        entry -> {
          entry.getValue().getFutureOutgoingChannel().channel().close();
          channelMap.remove(entry.getKey(), entry.getValue());
        });
  }

  public Collection<Map.Entry<ChannelId, ReusableChannel>> getEntries() {
    return new ArrayList<>(channelMap.entries());
  }

  public synchronized void addChannel(ChannelId channelId, ChannelFuture channelFuture) {
    addChannel(channelId, channelFuture, null);
  }

  public synchronized void addChannel(
      ChannelId channelId, ChannelFuture channelFuture, @Nullable Channel boundIncomingChannel) {
    channelMap.put(channelId, new ReusableChannel(channelFuture, boundIncomingChannel));
  }

  /**
   * Removes the pool entry wrapping exactly this outgoing channel (identity match) and closes it.
   * Matching on the {@link Channel} rather than on the {@link ChannelFuture} is deliberate: callers
   * generally only hold the channel, and a freshly derived future would never be {@code equal} to
   * the one stored in the pool.
   */
  public synchronized void remove(Channel outgoingChannel) {
    channelMap.entries().stream()
        .filter(
            entry -> entry.getValue().getFutureOutgoingChannel().channel().equals(outgoingChannel))
        .findFirst()
        .ifPresent(entry -> removeChannels(List.of(entry)));
  }

  public record ChannelId(InetSocketAddress remoteAddress) {
    public static ChannelId from(RequestInfo<?> info) {
      return new ChannelId(info.retrieveActualRemoteAddress());
    }

    public static ChannelId from(InetSocketAddress remoteAddress) {
      return new ChannelId(remoteAddress);
    }
  }
}
