/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/*
 * @author jamesdbloom
 */
public interface BinaryProxyListener {

  public void onProxy(
      BinaryMessage binaryRequest,
      CompletableFuture<BinaryMessage> binaryResponse,
      SocketAddress serverAddress,
      SocketAddress clientAddress);
}
