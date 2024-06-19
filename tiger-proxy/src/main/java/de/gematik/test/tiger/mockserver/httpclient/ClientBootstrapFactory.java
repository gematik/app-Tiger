/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.RESPONSE_FUTURE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.SECURE;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.model.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ClientBootstrapFactory {

  private final Configuration configuration;
  private final EventLoopGroup eventLoop;
  @Getter private final ReusableChannelMap channelMap = new ReusableChannelMap();

  /**
   * Method is private and should not be used directly.
   *
   * <p>It has many arguments which can be nullable. By using the builder we dont need to specify
   * all arguments and sane defaults are assumed when possible
   *
   * <p>Use ClientBootstrapFactor.builder()..connectToChannel() to create a Channel
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

    ChannelFuture existingChannel = null;
    if (requestInfo != null) {
      existingChannel =
          channelMap.getChannelToReuse(
              requestInfo); // if there is no request info then we are opening a new channel
      remoteAddress = requestInfo.getRemoteServerAddress();
      incomingChannel = requestInfo.getIncomingChannel();
    }

    if (existingChannel != null) {
      log.trace("reusing already existing channel");
      existingChannel.addListener(
          (ChannelFutureListener)
              future -> {
                if (future.isSuccess()) {
                  // reusing the channel, but we want to get the response in our new responseFuture
                  Optional.ofNullable(future.channel().attr(RESPONSE_FUTURE).get())
                      .ifPresent(oldFuture -> oldFuture.complete(null));
                  future.channel().attr(RESPONSE_FUTURE).set(responseFuture);
                }
              });
      if (onReuseListener != null) {
        existingChannel.addListener(onReuseListener);
      }
      return existingChannel;
    } else {
      log.trace("creating a new channel");
      if (timeoutInMilliseconds == null) {
        timeoutInMilliseconds = configuration.socketConnectionTimeoutInMillis();
      }
      if (eventLoopGroup == null) {
        eventLoopGroup = eventLoop;
      }

      var timeout = timeoutInMilliseconds != null ? timeoutInMilliseconds.intValue() : null;
      var bootstrap =
          new Bootstrap()
              .group(eventLoopGroup)
              .channel(NioSocketChannel.class)
              .option(ChannelOption.AUTO_READ, true)
              .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
              .option(
                  ChannelOption.WRITE_BUFFER_WATER_MARK,
                  new WriteBufferWaterMark(8 * 1024, 32 * 1024))
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
              .attr(SECURE, isSecure)
              .attr(REMOTE_SOCKET, remoteAddress)
              .attr(ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE, errorIfChannelClosedWithoutResponse)
              .handler(clientInitializer);
      if (responseFuture != null) {
        bootstrap.attr(RESPONSE_FUTURE, responseFuture);
      }
      var channelFuture = bootstrap.connect(remoteAddress);
      channelMap.addChannel(
          ReusableChannelMap.ChannelId.from(incomingChannel, remoteAddress), channelFuture);
      if (onCreationListener != null) {
        channelFuture.addListener(onCreationListener);
      }
      channelFuture.addListener(
          (ChannelFutureListener)
              future ->
                  // when the channel is closed we remove it from the channelsMap
                  future.channel().closeFuture().addListener(f -> channelMap.remove(future)));
      return channelFuture;
    }
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
