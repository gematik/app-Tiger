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

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.ReusableChannelMap.ChannelId;
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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import jdk.net.ExtendedSocketOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class ClientBootstrapFactory {

  public static final AttributeKey<Integer> LOOP_COUNTER = AttributeKey.valueOf("loopCounter");
  private final MockServerConfiguration configuration;
  private final EventLoopGroup eventLoop;
  @Getter private final ReusableChannelMap channelMap = new ReusableChannelMap();
  private final ScheduledExecutorService cleanupScheduler =
      new ScheduledThreadPoolExecutor(
          1,
          r -> {
            var t = new Thread(r, "TGR-CONNECTION-POOL-CLEANUP");
            t.setDaemon(true);
            return t;
          });

  public void shutdown() {
    cleanupScheduler.shutdownNow();
  }

  public ClientBootstrapFactory(MockServerConfiguration configuration, EventLoopGroup eventLoop) {
    this.configuration = configuration;
    this.eventLoop = eventLoop;
    // Start periodic cleanup task: run every minute
    cleanupScheduler.scheduleAtFixedRate(
        channelMap::cleanupExpiredChannels, 1, 1, TimeUnit.MINUTES);
  }

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
      @Nullable EventLoopGroup eventLoopGroup,
      boolean forceNewChannel,
      boolean dedicatedChannel) {

    val resolvedParams = resolveChannelParameters(requestInfo, incomingChannel, remoteAddress);
    val explicitOutgoingChannel = requestInfo != null ? requestInfo.getOutgoingChannel() : null;
    if (!forceNewChannel && explicitOutgoingChannel != null && explicitOutgoingChannel.isActive()) {
      return reuseExistingChannel(
          explicitOutgoingChannel.newSucceededFuture(),
          resolvedParams.incomingChannel(),
          responseFuture,
          onReuseListener);
    }

    val channelToReuse =
        forceNewChannel
            ? null
            : Optional.ofNullable(requestInfo)
                .map(info -> channelMap.getChannelToReuse(info, dedicatedChannel))
                .orElse(null);
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
        requestInfo,
        config,
        resolvedParams.incomingChannel(),
        resolvedParams.remoteAddress(),
        onCreationListener,
        dedicatedChannel);
  }

  /** Remove a channel from the pool (e.g., when it fails during reuse). */
  public void removeChannelFromPool(Channel outgoingChannel) {
    channelMap.remove(outgoingChannel);
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

                // Transfer ownership: previous incoming must NOT close the outgoing on disconnect,
                // otherwise the newly bound incoming would see a dead channel. See
                // HttpRequestHandler.channelInactive which closes attr(OUTGOING_CHANNEL).
                Channel previousIncoming = future.channel().attr(INCOMING_CHANNEL).get();
                if (previousIncoming != null && previousIncoming != incomingChannel) {
                  previousIncoming.attr(BinaryBridgeHandler.OUTGOING_CHANNEL).set(null);
                }
                future.channel().attr(INCOMING_CHANNEL).set(incomingChannel);
                incomingChannel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL).set(future.channel());
              }
            });

    if (onReuseListener != null) {
      existingChannel.addListener(onReuseListener);
    }
    return existingChannel;
  }

  private ChannelFuture createNewChannel(
      RequestInfo<?> requestInfo,
      ChannelConfig config,
      Channel incomingChannel,
      InetSocketAddress remoteAddress,
      @Nullable ChannelFutureListener onCreationListener,
      boolean dedicatedChannel) {
    log.trace("creating a new channel");

    val timeout = resolveTimeout(config.timeoutInMilliseconds());
    val effectiveEventLoopGroup =
        config.eventLoopGroup() != null ? config.eventLoopGroup() : eventLoop;

    var channelFuture =
        createBootstrap(config, incomingChannel, remoteAddress, timeout, effectiveEventLoopGroup)
            .connect(remoteAddress);
    registerNewChannel(
        requestInfo,
        channelFuture,
        incomingChannel,
        remoteAddress,
        onCreationListener,
        dedicatedChannel);

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
      RequestInfo<?> requestInfo,
      ChannelFuture channelFuture,
      Channel incomingChannel,
      InetSocketAddress remoteAddress,
      @Nullable ChannelFutureListener onCreationListener,
      boolean dedicatedChannel) {

    Channel boundIncomingChannel = requestInfo == null || dedicatedChannel ? incomingChannel : null;
    channelMap.addChannel(ChannelId.from(remoteAddress), channelFuture, boundIncomingChannel);

    if (onCreationListener != null) {
      channelFuture.addListener(onCreationListener);
    }

    channelFuture.addListener(
        (ChannelFutureListener)
            future ->
                // NOTE: Do NOT remove from pool on close. Channels stay in pool until explicitly
                // cleaned up. ReusableChannel.canBeReused() checks if channel is still active
                // before allowing reuse. This allows connection keep-alive to work: if backend
                // closes
                // the connection, next request will see channel is inactive and create a new one.
                incomingChannel.attr(BinaryBridgeHandler.OUTGOING_CHANNEL).set(future.channel()));
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
}
