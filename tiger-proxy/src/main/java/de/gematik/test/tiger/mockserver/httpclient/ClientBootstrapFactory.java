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

import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.RESPONSE_FUTURE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.SECURE;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.ClientBootstrapFactory.ReusableChannelMap.ChannelId;
import de.gematik.test.tiger.mockserver.model.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import jdk.net.ExtendedSocketOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@RequiredArgsConstructor
@Slf4j
public class ClientBootstrapFactory {

  public static final AttributeKey<Integer> LOOP_COUNTER = AttributeKey.valueOf("loopCounter");
  private final MockServerConfiguration configuration;
  private final EventLoopGroup eventLoop;
  @Getter private final ReusableChannelMap channelMap = new ReusableChannelMap();

  /** Groups channel configuration parameters to reduce method parameter count. */
  @Builder
  public record ChannelConfig(
      boolean isSecure,
      boolean errorIfChannelClosedWithoutResponse,
      @Nullable CompletableFuture<Message> responseFuture,
      @Nullable Long timeoutInMilliseconds,
      @Nullable EventLoopGroup eventLoopGroup,
      HttpClientInitializer clientInitializer) {}

  /**
   * Method is private and should not be used directly.
   *
   * <p>It has many arguments which can be nullable. By using the builder we dont need to specify
   * all arguments and sane defaults are assumed when possible
   *
   * <p>Use ClientBootstrapFactor.configureChannel()..connectToChannel() to create a Channel
   */
  @Builder(builderMethodName = "configureChannel", buildMethodName = "connectToChannel")
  private ChannelFuture createOrReuseChannel(
      boolean isSecure,
      @Nullable RequestInfo<?> requestInfo,
      @Nullable Channel incomingChannel,
      @Nullable InetSocketAddress remoteAddress,
      HttpClientInitializer clientInitializer,
      boolean errorIfChannelClosedWithoutResponse,
      @Nullable CompletableFuture<Message> responseFuture,
      @Nullable ChannelFutureListener onCreationListener,
      @Nullable ChannelFutureListener onReuseListener,
      @Nullable Long timeoutInMilliseconds,
      @Nullable EventLoopGroup eventLoopGroup) {

    val resolvedParams = resolveChannelParameters(requestInfo, incomingChannel, remoteAddress);

    val channelToReuse =
        Optional.ofNullable(requestInfo).map(channelMap::getChannelToReuse).orElse(null);
    if (channelToReuse != null) {
      return reuseExistingChannel(
          channelToReuse, resolvedParams.incomingChannel(), responseFuture, onReuseListener);
    }

    val config =
        ChannelConfig.builder()
            .isSecure(isSecure)
            .errorIfChannelClosedWithoutResponse(errorIfChannelClosedWithoutResponse)
            .responseFuture(responseFuture)
            .timeoutInMilliseconds(timeoutInMilliseconds)
            .eventLoopGroup(eventLoopGroup)
            .clientInitializer(clientInitializer)
            .build();

    return createNewChannel(
        config,
        resolvedParams.incomingChannel(),
        resolvedParams.remoteAddress(),
        onCreationListener);
  }

  private record ResolvedChannelParams(Channel incomingChannel, InetSocketAddress remoteAddress) {}

  private ResolvedChannelParams resolveChannelParameters(
      @Nullable RequestInfo<?> requestInfo,
      @Nullable Channel incomingChannel,
      @Nullable InetSocketAddress remoteAddress) {
    if (requestInfo != null) {
      return new ResolvedChannelParams(
          requestInfo.getIncomingChannel(), requestInfo.retrieveActualRemoteAddress());
    }
    return new ResolvedChannelParams(incomingChannel, remoteAddress);
  }

  private ChannelFuture reuseExistingChannel(
      ChannelFuture existingChannel,
      Channel incomingChannel,
      @Nullable CompletableFuture<Message> responseFuture,
      @Nullable ChannelFutureListener onReuseListener) {
    log.trace("reusing already existing channel");

    existingChannel.addListener(
        (ChannelFutureListener)
            future -> {
              if (future.isSuccess()) {
                // Complete any old response future before setting the new one
                Optional.ofNullable(future.channel().attr(RESPONSE_FUTURE).get())
                    .ifPresent(oldFuture -> oldFuture.complete(null));

                future.channel().attr(RESPONSE_FUTURE).set(responseFuture);
                incomingChannel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL).set(future.channel());
              }
            });

    if (onReuseListener != null) {
      existingChannel.addListener(onReuseListener);
    }
    return existingChannel;
  }

  private ChannelFuture createNewChannel(
      ChannelConfig config,
      Channel incomingChannel,
      InetSocketAddress remoteAddress,
      @Nullable ChannelFutureListener onCreationListener) {
    log.trace("creating a new channel");

    val timeout = resolveTimeout(config.timeoutInMilliseconds());
    val effectiveEventLoopGroup =
        config.eventLoopGroup() != null ? config.eventLoopGroup() : eventLoop;

    var channelFuture =
        createBootstrap(config, incomingChannel, remoteAddress, timeout, effectiveEventLoopGroup)
            .connect(remoteAddress);
    registerNewChannel(channelFuture, incomingChannel, remoteAddress, onCreationListener);

    return channelFuture;
  }

  private Integer resolveTimeout(@Nullable Long timeoutInMilliseconds) {
    if (timeoutInMilliseconds != null) {
      return timeoutInMilliseconds.intValue();
    }
    val configuredTimeout = configuration.socketConnectionTimeoutInMillis();
    return configuredTimeout != null ? configuredTimeout.intValue() : null;
  }

  private Bootstrap createBootstrap(
      ChannelConfig config,
      Channel incomingChannel,
      InetSocketAddress remoteAddress,
      @Nullable Integer timeout,
      EventLoopGroup effectiveEventLoopGroup) {

    int loopCounter =
        incomingChannel.hasAttr(LOOP_COUNTER) ? incomingChannel.attr(LOOP_COUNTER).get() + 1 : 0;

    var bootstrap =
        new Bootstrap()
            .group(effectiveEventLoopGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.AUTO_READ, true)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(
                ChannelOption.WRITE_BUFFER_WATER_MARK,
                new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
            .attr(SECURE, config.isSecure())
            .attr(REMOTE_SOCKET, remoteAddress)
            .attr(
                ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE,
                config.errorIfChannelClosedWithoutResponse())
            .attr(LOOP_COUNTER, loopCounter)
            .attr(INCOMING_CHANNEL, incomingChannel)
            .resolver(
                config.clientInitializer().usesForwardProxy()
                    ? NoopAddressResolverGroup.INSTANCE
                    : DefaultAddressResolverGroup.INSTANCE)
            .handler(config.clientInitializer());

    Optional.ofNullable(config.responseFuture())
        .ifPresent(responseFuture -> bootstrap.attr(RESPONSE_FUTURE, responseFuture));

    Optional.ofNullable(configuration.tcpIdleTimeoutInMillis())
        .ifPresent(
            timeoutMillis -> {
              if (timeoutMillis > 0) {
                bootstrap
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(
                        NioChannelOption.of(ExtendedSocketOptions.TCP_KEEPINTERVAL), timeoutMillis);
              }
            });
    return bootstrap;
  }

  private void registerNewChannel(
      ChannelFuture channelFuture,
      Channel incomingChannel,
      InetSocketAddress remoteAddress,
      @Nullable ChannelFutureListener onCreationListener) {

    channelMap.addChannel(ChannelId.from(incomingChannel, remoteAddress), channelFuture);

    if (onCreationListener != null) {
      channelFuture.addListener(onCreationListener);
    }

    channelFuture.addListener(
        (ChannelFutureListener)
            future -> {
              future.channel().closeFuture().addListener(f -> channelMap.remove(future));
              incomingChannel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL).set(future.channel());
            });
  }

  public int getLoopCounterForOpenConnectionFromPort(int port) {
    return channelMap.getEntries().stream()
        .filter(e -> isLocalPortOfChannelEqualToIncomingPortInQuestion(port, e))
        .mapToInt(
            entry -> entry.getValue().getFutureOutgoingChannel().channel().attr(LOOP_COUNTER).get())
        .max()
        .orElse(0);
  }

  private boolean isLocalPortOfChannelEqualToIncomingPortInQuestion(
      int port, Entry<ChannelId, ReusableChannel> entry) {
    val loc = entry.getValue().getFutureOutgoingChannel().channel().localAddress();
    if (loc instanceof InetSocketAddress localAddress) {
      return localAddress.getPort() == port;
    }
    return false;
  }

  public static class ReusableChannelMap {
    private final Multimap<ChannelId, ReusableChannel> channelMap =
        Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    public synchronized ChannelFuture getChannelToReuse(RequestInfo<?> requestInfo) {
      return channelMap.get(ChannelId.from(requestInfo)).stream()
          .filter(ReusableChannel::canBeReused)
          .findAny()
          .map(ReusableChannel::getFutureOutgoingChannel)
          .orElse(null);
    }

    public Collection<Entry<ChannelId, ReusableChannel>> getEntries() {
      return new ArrayList<>(channelMap.entries());
    }

    public synchronized ChannelFuture getChannelInUse(RequestInfo<?> requestInfo) {
      return channelMap.get(ChannelId.from(requestInfo)).stream()
          .findAny()
          .map(ReusableChannel::getFutureOutgoingChannel)
          .orElse(null);
    }

    public synchronized void addChannel(ChannelId channelId, ChannelFuture channelFuture) {
      channelMap.put(channelId, new ReusableChannel(channelFuture));
    }

    public synchronized void remove(ChannelFuture channelFuture) {
      var toRemove =
          channelMap.entries().stream()
              .filter(entry -> entry.getValue().getFutureOutgoingChannel().equals(channelFuture))
              .toList();
      toRemove.forEach(entry -> channelMap.remove(entry.getKey(), entry.getValue()));
    }

    public record ChannelId(Channel incomingChannel, InetSocketAddress remoteAddress) {
      public static ChannelId from(RequestInfo<?> info) {
        return from(info.getIncomingChannel(), info.getRemoteServerAddress());
      }

      public static ChannelId from(Channel incomingChannel, InetSocketAddress remoteAddress) {
        return new ChannelId(incomingChannel, remoteAddress);
      }
    }
  }
}
