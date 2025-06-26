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
package de.gematik.test.tiger.mockserver.netty;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_PROXY_MAX_LOOP_COUNTER;
import static de.gematik.test.tiger.mockserver.httpclient.ClientBootstrapFactory.LOOP_COUNTER;

import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import lombok.AllArgsConstructor;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AllArgsConstructor
public class MockServerInfiniteLoopChecker {

  private static final Logger log = LoggerFactory.getLogger(MockServerInfiniteLoopChecker.class);
  private final NettyHttpClient nettyHttpClient;

  public boolean isInfiniteLoop(Channel incomingChannel) {
    if (incomingChannel == null) {
      return false;
    }
    final SocketAddress socketAddress = incomingChannel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress remoteClientAddress) {
      val loopCounter = nettyHttpClient.queryClientPort(remoteClientAddress.getPort());
      incomingChannel.attr(LOOP_COUNTER).set(loopCounter);
      return loopCounter > TIGER_PROXY_MAX_LOOP_COUNTER.getValueOrDefault();
    } else {
      return false;
    }
  }
}
