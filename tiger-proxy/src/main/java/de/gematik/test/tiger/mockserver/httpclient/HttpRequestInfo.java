/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/** Combines multiple arguments required to send a HTTP request with the {@link NettyHttpClient} */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HttpRequestInfo extends RequestInfo<HttpRequest> {
  public HttpRequestInfo(
      Channel incomingChannel, HttpRequest dataToSend, InetSocketAddress remoteServerAddress) {
    super(incomingChannel, dataToSend, remoteServerAddress);
  }
}
