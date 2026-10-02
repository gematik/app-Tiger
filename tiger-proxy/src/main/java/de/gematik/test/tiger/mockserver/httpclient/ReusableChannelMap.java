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
import com.google.common.net.InetAddresses;
import de.gematik.rbellogger.util.RbelInternetAddress;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class ReusableChannelMap {
  private final Multimap<ChannelId, ReusableChannel> channelMap =
      Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
  public static final long DEFAULT_CHANNEL_POOL_TTL_MILLIS = 5L * 60 * 1000;
  public static final int DEFAULT_MAX_CHANNELS_PER_KEY = 32;

  @Getter @Setter private long channelPoolTtlMillis = DEFAULT_CHANNEL_POOL_TTL_MILLIS;
  @Getter @Setter private int maxChannelsPerKey = DEFAULT_MAX_CHANNELS_PER_KEY;

  /**
   * Finds a pooled outgoing channel this request may reuse, evicting any that no longer point where
   * their hostname now resolves.
   */
  public ChannelFuture getChannelToReuse(RequestInfo<?> requestInfo) {
    val channelId = ChannelId.from(requestInfo);

    if (nothingPooledFor(channelId)) {
      return null;
    }

    // Deliberately outside the monitor: this can block on the OS resolver, and holding the pool
    // lock across it would put every other thread behind a network call.
    val currentAddress = resolveDestinationOf(requestInfo, channelId);

    synchronized (this) {
      evictChannelsPointingElsewhere(channelId, currentAddress);

      val reusableChannel =
          channelMap.get(channelId).stream()
              .filter(ReusableChannel::canBeReused)
              .findFirst()
              .orElse(null);

      if (reusableChannel != null) {
        reusableChannel.markAsUsed();
        // Re-insert at end to maintain sorted order (oldest first)
        channelMap.remove(channelId, reusableChannel);
        channelMap.put(channelId, reusableChannel);
        return reusableChannel.getFutureOutgoingChannel();
      }
      return null;
    }
  }

  /** Skipping the lookup for an empty bucket keeps the resolution off the common path. */
  private boolean nothingPooledFor(ChannelId channelId) {
    return channelMap.get(channelId).isEmpty();
  }

  /**
   * Where this request is headed right now; empty when there is no name to re-resolve, so a
   * transient DNS failure leaves pooled channels alone rather than evicting them. Must be called
   * outside the pool monitor - it blocks on the OS resolver when the DNS cache misses.
   */
  private static Optional<InetAddress> resolveDestinationOf(
      RequestInfo<?> requestInfo, ChannelId channelId) {
    return destinationName(requestInfo, channelId)
        .flatMap(name -> new RbelInternetAddress(name, null).toInetAddress());
  }

  private static Optional<String> destinationName(RequestInfo<?> requestInfo, ChannelId channelId) {
    return Stream.of(hostStringOf(requestInfo.retrieveActualRemoteAddress()), channelId.host())
        .filter(Objects::nonNull)
        .filter(name -> !InetAddresses.isInetAddress(name))
        .findFirst();
  }

  private static String hostStringOf(@Nullable InetSocketAddress address) {
    return address == null ? null : address.getHostString();
  }

  /** Drops pooled channels whose socket points where the hostname no longer resolves. */
  private void evictChannelsPointingElsewhere(
      ChannelId channelId, Optional<InetAddress> currentAddress) {
    if (currentAddress.isEmpty()) {
      return;
    }
    val stale =
        channelMap.get(channelId).stream()
            .filter(channel -> !channel.hasRequestInFlight())
            .filter(channel -> pointsSomewhereElseThan(channel, currentAddress.get()))
            .toList();
    if (!stale.isEmpty()) {
      log.atDebug()
          .addArgument(stale::size)
          .addArgument(channelId::host)
          .log(
              "Evicting {} pooled channel(s) for {} whose remote IP no longer matches current DNS");
      removeChannels(stale);
    }
  }

  /**
   * Whether this channel was opened towards an address the hostname no longer resolves to.
   *
   * <p>Asks {@code REMOTE_SOCKET} rather than the socket's own remote address. With a forward proxy
   * configured, netty's {@code ProxyHandler} connects the socket to the proxy and tunnels to the
   * target, so the socket's peer is the proxy - comparing that against the target's DNS would find
   * a mismatch for every pooled channel and close the lot on every lookup. {@code REMOTE_SOCKET}
   * records where the request was headed, which is what a reroute is about either way.
   */
  private static boolean pointsSomewhereElseThan(ReusableChannel channel, InetAddress current) {
    val target =
        channel.getFutureOutgoingChannel().channel().attr(NettyHttpClient.REMOTE_SOCKET).get();
    return target != null && target.getAddress() != null && !current.equals(target.getAddress());
  }

  /**
   * Periodically clean up the pool: dead channels always, idle ones once they pass the TTL.
   *
   * <p>Dead channels are reclaimed regardless of age because nothing else reaches them. Closing a
   * channel does not remove it from the pool, and a dead channel is never handed out, so it can
   * never fail in use and reach {@link #remove(Channel)} either. {@link #removeAllFor} takes care
   * of a departing client's channels, which leaves this sweep responsible for the other case: a
   * backend that closed its connection while its client stayed put.
   *
   * <p>A channel with a request in flight is never reclaimed, however long it has been in the pool:
   * {@code lastUsedAt} only advances on handout, so a slow backend can push an in-flight channel
   * past the TTL and closing it would abort a live exchange.
   */
  public synchronized void cleanupExpiredChannels() {
    var reclaimable =
        channelMap.values().stream()
            .filter(channel -> channel.isDead() || isReclaimableIdleChannel(channel))
            .toList();
    removeChannels(reclaimable);
  }

  private boolean isReclaimableIdleChannel(ReusableChannel channel) {
    return channel.isExpired(channelPoolTtlMillis) && !channel.hasRequestInFlight();
  }

  /**
   * Evicts the oldest reclaimable channels for a key once it exceeds {@link #maxChannelsPerKey}.
   * Without this the pool has no bound at all - the TTL sweep is its only reclamation path, so a
   * client firing concurrent requests at one backend can grow its bucket without limit.
   *
   * <p>The bound is deliberately soft. The overage is counted against every pooled channel, but a
   * channel with a request in flight is never evicted, so a bucket whose channels are all busy
   * exceeds the cap until they are not. Aborting a live exchange to hit a number would be the worse
   * trade. It settles by itself: a completed response - including one that completed exceptionally
   * - makes the channel evictable again, and the next {@link #addChannel} call reclaims it.
   */
  private void enforceMaxChannelsPerKey(ChannelId channelId) {
    val pooled = channelMap.get(channelId);
    val overage = pooled.size() - maxChannelsPerKey;
    if (overage <= 0) {
      return;
    }
    val evictable =
        pooled.stream()
            .filter(channel -> channel.isDead() || !channel.hasRequestInFlight())
            .limit(overage)
            .toList();
    if (evictable.size() < overage) {
      log.atDebug()
          .addArgument(channelId)
          .addArgument(overage)
          .addArgument(maxChannelsPerKey)
          .addArgument(evictable::size)
          .log(
              "Pool for {} is {} over the limit of {} but only {} channel(s) could be evicted; the"
                  + " rest have requests in flight");
    }
    if (!evictable.isEmpty()) {
      log.atDebug()
          .addArgument(channelId)
          .addArgument(maxChannelsPerKey)
          .addArgument(evictable::size)
          .log("Pool for {} exceeded {} channels; evicting {} channel(s)");
      removeChannels(evictable);
    }
  }

  /**
   * Closes every pooled channel that was opened for this incoming channel.
   *
   * <p>The client-side attribute {@code OUTGOING_CHANNEL} holds a single channel and is overwritten
   * each time, so closing it on disconnect only ever reaches the last one. One client connection
   * can easily hold several backend channels - two destinations land in two buckets, and concurrent
   * requests to one destination open extra channels because an in-flight channel cannot be reused.
   * The pool keys on the incoming channel, so it is the one place that can name all of them.
   *
   * <p>The leftovers are not dead, just unreachable, so the TTL sweep would eventually take them;
   * this closes them at once, which is what TGR-2182 asks for.
   */
  public synchronized void removeAllFor(Channel incomingChannel) {
    val orphaned =
        channelMap.values().stream()
            .filter(channel -> incomingChannel.equals(channel.getChannelId().incomingChannel()))
            .toList();
    if (!orphaned.isEmpty()) {
      log.atDebug()
          .addArgument(orphaned::size)
          .log("Closing {} backend channel(s) opened by a client connection that has gone away");
      removeChannels(orphaned);
    }
  }

  private void removeChannels(List<ReusableChannel> toRemove) {
    toRemove.forEach(
        channel -> {
          channel.getFutureOutgoingChannel().channel().close();
          channelMap.remove(channel.getChannelId(), channel);
        });
  }

  public synchronized Collection<Map.Entry<ChannelId, ReusableChannel>> getEntries() {
    return List.copyOf(channelMap.entries());
  }

  public synchronized void addChannel(ChannelId channelId, ChannelFuture channelFuture) {
    channelMap.put(channelId, new ReusableChannel(channelId, channelFuture));
    enforceMaxChannelsPerKey(channelId);
  }

  /**
   * Drops the pool entry for this outgoing channel <em>without</em> closing it, handing ownership
   * to whoever asked (TESTHUB-261).
   *
   * <p>Used when a connection is upgraded to a tunnel. A tunnel is not a reusable HTTP connection:
   * it may never be handed to another request, and the pool's own reclamation must not reach it
   * either. Both {@link #cleanupExpiredChannels} and {@link #enforceMaxChannelsPerKey} would
   * happily take it - {@code lastUsedAt} only advances when the pool hands a channel out, and
   * tunnelled traffic never does, so a busy tunnel looks permanently idle and ages out of the pool
   * while carrying data. Leaving the pool is simpler and safer than teaching every eviction path
   * about tunnels.
   *
   * <p>The channel is not orphaned by this: {@code BinaryHandler} and {@code BinaryBridgeHandler}
   * close each half of a tunnel when the other goes away.
   */
  public synchronized void forget(Channel outgoingChannel) {
    channelMap
        .values()
        .removeIf(channel -> channel.getFutureOutgoingChannel().channel().equals(outgoingChannel));
  }

  /**
   * Removes the pool entry wrapping exactly this outgoing channel (identity match) and closes it.
   * Matching on the {@link Channel} rather than on the {@link ChannelFuture} is deliberate: callers
   * generally only hold the channel, and a freshly derived future would never be {@code equal} to
   * the one stored in the pool.
   */
  public synchronized void remove(Channel outgoingChannel) {
    channelMap.values().stream()
        .filter(channel -> channel.getFutureOutgoingChannel().channel().equals(outgoingChannel))
        .findFirst()
        .ifPresent(channel -> removeChannels(List.of(channel)));
  }

  /**
   * Pool key: the destination as the request wrote it, per downstream client.
   *
   * <p>The incoming channel is part of it so a backend connection is only ever handed back to the
   * client that opened it - otherwise one client inherits whatever the backend scoped to that
   * socket, connection-bound authentication (NTLM, Negotiate) above all.
   *
   * <p>The resolved IP is deliberately absent. Including it would defeat {@link
   * #evictChannelsPointingElsewhere}: a rerouted hostname would land in a different bucket, leaving
   * the channels aimed at the old address unreachable and so never evicted.
   */
  public record ChannelId(Channel incomingChannel, String host, int port, boolean secure) {

    public ChannelId {
      // Host names are case-insensitive (RFC 4343), so the same host written two ways must not end
      // up in two buckets.
      host = host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    public static ChannelId from(RequestInfo<?> info) {
      val remoteAddress = info.retrieveActualRemoteAddress();
      String host = null;
      boolean secure = false;
      if (info.getDataToSend() instanceof HttpRequest httpRequest) {
        host = httpRequest.requestedHostname().orElse(null);
        secure = Boolean.TRUE.equals(httpRequest.isSecure());
      }
      if (host == null) {
        host = remoteAddress == null ? null : remoteAddress.getHostString();
      }
      return new ChannelId(
          info.getIncomingChannel(),
          host,
          remoteAddress == null ? -1 : remoteAddress.getPort(),
          secure);
    }

    public static ChannelId from(Channel incomingChannel, InetSocketAddress remoteAddress) {
      return new ChannelId(
          incomingChannel,
          remoteAddress == null ? null : remoteAddress.getHostString(),
          remoteAddress == null ? -1 : remoteAddress.getPort(),
          false);
    }
  }
}
