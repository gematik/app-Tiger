/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
