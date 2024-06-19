/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

import de.gematik.test.tiger.mockserver.model.Message;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
public class RequestInfo<T extends Message> {
  private final Channel incomingChannel;
  private T dataToSend;
  private InetSocketAddress remoteServerAddress;
}
