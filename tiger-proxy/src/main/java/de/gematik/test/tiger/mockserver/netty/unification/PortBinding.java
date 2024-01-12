package de.gematik.test.tiger.mockserver.netty.unification;

import de.gematik.test.tiger.mockserver.model.ObjectWithJsonToString;
import java.net.InetSocketAddress;

public class PortBinding extends ObjectWithJsonToString {

  private final InetSocketAddress inetSocketAddress;
  private final String portExtension;

  public PortBinding(InetSocketAddress inetSocketAddress, String portExtension) {
    this.inetSocketAddress = inetSocketAddress;
    this.portExtension = portExtension;
  }

  public InetSocketAddress getInetSocketAddress() {
    return inetSocketAddress;
  }

  public String getPortExtension() {
    return portExtension;
  }
}
