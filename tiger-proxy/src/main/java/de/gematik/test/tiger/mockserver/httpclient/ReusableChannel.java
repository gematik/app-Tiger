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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * It wraps a ChannelFuture that can be reused if the corresponding response future is already done.
 */
@Getter
@EqualsAndHashCode(exclude = {"lastUsedAt", "boundIncomingChannel"})
@RequiredArgsConstructor
@Slf4j
public class ReusableChannel {

  private final ChannelFuture futureOutgoingChannel;

  /**
   * If non-null, this channel is bound to a specific incoming channel and may only be reused by
   * that same incoming channel (e.g., direct-forward bridges). If null, the channel is freely
   * reusable by any incoming channel to the same remote address.
   */
  @Nullable private final Channel boundIncomingChannel;

  private long lastUsedAt = System.currentTimeMillis();

  public boolean canBeReusedBy(@Nullable Channel requestingIncomingChannel) {
    return canBeReusedBy(requestingIncomingChannel, false);
  }

  /**
   * @param requireBoundChannel if true, only a channel already bound to exactly this incoming
   *     channel qualifies. Unbound (freely poolable) channels are rejected. This is what makes a
   *     dedicated 1:1 bridge reuse its own in-flight channel for follow-up fragments without ever
   *     latching onto a channel belonging to another tunnel.
   */
  public boolean canBeReusedBy(
      @Nullable Channel requestingIncomingChannel, boolean requireBoundChannel) {
    if (requireBoundChannel) {
      if (boundIncomingChannel == null || boundIncomingChannel != requestingIncomingChannel) {
        return false;
      }
    } else if (boundIncomingChannel != null && boundIncomingChannel != requestingIncomingChannel) {
      return false;
    }
    return canBeReused();
  }

  public boolean canBeReused() {
    if (futureOutgoingChannel.isDone() && !futureOutgoingChannel.channel().isActive()) {
      return false;
    }

    boolean shouldWait = SHOULD_I_WAIT_FOR_A_RESPONSE_BEFORE_REUSING.test(futureOutgoingChannel);
    boolean isDone = IS_RESPONSE_DONE.test(futureOutgoingChannel);
    return !shouldWait || isDone;
  }

  public void markAsUsed() {
    lastUsedAt = System.currentTimeMillis();
  }

  public boolean isExpired(long ttlMillis) {
    long idleMillis = System.currentTimeMillis() - lastUsedAt;
    return idleMillis > ttlMillis;
  }

  private static final Predicate<ChannelFuture> IS_RESPONSE_DONE =
      f ->
          Optional.ofNullable(f.channel().attr(NettyHttpClient.RESPONSE_FUTURE).get())
              .map(CompletableFuture::isDone)
              .orElse(Boolean.TRUE);

  private static final Predicate<ChannelFuture> SHOULD_I_WAIT_FOR_A_RESPONSE_BEFORE_REUSING =
      f ->
          Optional.ofNullable(
                  f.channel().attr(NettyHttpClient.ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE).get())
              .orElse(Boolean.FALSE);
}
