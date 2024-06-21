/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.httpclient;

import io.netty.channel.ChannelFuture;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * It wraps a ChannelFuture that can be reused if the corresponding response future is already done.
 */
@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
@Slf4j
public class ReusableChannel {

  private final ChannelFuture futureOutgoingChannel;

  public boolean canBeReused() {
    return !SHOULD_I_WAIT_FOR_A_RESPONSE_BEFORE_REUSING.test(futureOutgoingChannel)
        || IS_RESPONSE_DONE.test(futureOutgoingChannel);
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
