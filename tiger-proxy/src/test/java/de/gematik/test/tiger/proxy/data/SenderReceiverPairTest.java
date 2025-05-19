package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class SenderReceiverPairTest {
  @Test
  void testEqualityForOppositeDirection() {
    SocketAddress address1 = new InetSocketAddress("localhost", 8080);
    SocketAddress address2 = new InetSocketAddress("localhost", 9090);

    TcpIpConnectionIdentifier pair1 = new TcpIpConnectionIdentifier(address1, address2);
    TcpIpConnectionIdentifier pair2 = new TcpIpConnectionIdentifier(address2, address1);

    assertThat(pair1)
        .withFailMessage("Pairs with opposite directions should be equal")
        .isEqualTo(pair2);
  }

  @Test
  void testPrimaryAndSecondaryAddresses() {
    SocketAddress address1 = new InetSocketAddress("localhost", 8080);
    SocketAddress address2 = new InetSocketAddress("localhost", 9090);

    TcpIpConnectionIdentifier pair = new TcpIpConnectionIdentifier(address1, address2);

    assertThat(pair.sender())
        .withFailMessage("Primary address should be one of the addresses")
        .isIn(address1, address2);
    assertThat(pair.receiver())
        .withFailMessage("Secondary address should be one of the addresses")
        .isIn(address1, address2);
  }

  @Test
  void testSenderAndReceiver() {
    SocketAddress address1 = new InetSocketAddress("localhost", 8080);
    SocketAddress address2 = new InetSocketAddress("localhost", 9090);

    TcpIpConnectionIdentifier pair = new TcpIpConnectionIdentifier(address1, address2);

    assertThat(pair.sender()).withFailMessage("Sender should be address1").isEqualTo(address1);
    assertThat(pair.receiver()).withFailMessage("Receiver should be address2").isEqualTo(address2);
  }
}
