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

import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Combines multiple arguments required to send a binary request with the {@link NettyHttpClient}
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BinaryRequestInfo extends RequestInfo<BinaryMessage> {
  public BinaryRequestInfo(
      Channel incomingChannel, BinaryMessage dataToSend, InetSocketAddress remoteServerAddress) {
    super(incomingChannel, dataToSend, remoteServerAddress);
  }

  public byte[] getBytes() {
    return getDataToSend().getBytes();
  }
}