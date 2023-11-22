package de.gematik.test.tiger.common;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import java.io.IOException;
import java.net.ServerSocket;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SocketHelper {

  public static int findFreePort() {
    try (final ServerSocket serverSocket = new ServerSocket()) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new TigerConfigurationException("Error finding free port for admin-interface", e);
    }
  }
}
