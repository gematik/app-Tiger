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
package de.gematik.test.tiger.proxy.data;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@EqualsAndHashCode(of = {"sortedAddress1", "sortedAddress2"})
@Accessors(fluent = true)
public class TcpIpConnectionIdentifier {
  SocketAddress sender;
  SocketAddress receiver;
  boolean originalDirection;

  InetSocketAddress sortedAddress1;
  InetSocketAddress sortedAddress2;

  public TcpIpConnectionIdentifier(SocketAddress sender, SocketAddress receiver) {
    InetSocketAddress normA = normalize(sender);
    InetSocketAddress normB = normalize(receiver);

    if (compareAddresses(normA, normB) < 0) {
      this.sortedAddress1 = normA;
      this.sortedAddress2 = normB;
      this.originalDirection = true;
    } else {
      this.sortedAddress1 = normB;
      this.sortedAddress2 = normA;
      this.originalDirection = false;
    }
    this.sender = sender;
    this.receiver = receiver;
  }

  private static InetSocketAddress normalize(SocketAddress address) {

    if (!(address instanceof InetSocketAddress inetAddr)) {
      throw new IllegalArgumentException("Unsupported SocketAddress type: " + address.getClass());
    }

    // Try to get a resolved IP address
    if (inetAddr.getAddress() != null) {
      return new InetSocketAddress(inetAddr.getAddress().getHostAddress(), inetAddr.getPort());
    }

    // Fallback: If the address is unresolved, use the hostname as a last resort
    return new InetSocketAddress(inetAddr.getHostString(), inetAddr.getPort());
  }

  private static int compareAddresses(InetSocketAddress a, InetSocketAddress b) {
    if (a.getAddress() != null
        && b.getAddress() != null
        && a.getAddress().getHostAddress() != null
        && b.getAddress().getHostAddress() != null) {
      // prefer the resolved view, since it is more stable
      int ipCompare = a.getAddress().getHostAddress().compareTo(b.getAddress().getHostAddress());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    } else {
      // for non-resolvable addresses, use the hostname
      int ipCompare = a.getHostName().compareTo(b.getHostName());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    }
  }

  public boolean isSameDirectionAs(TcpIpConnectionIdentifier other) {
    return this.originalDirection == other.originalDirection;
  }

  public String printDirectionSymbol() {
    if (originalDirection) {
      return "->";
    } else {
      return "<-";
    }
  }

  public TcpIpConnectionIdentifier reverse() {
    return new TcpIpConnectionIdentifier(receiver, sender);
  }

  public String toString() {
    return "{" + sortedAddress1 + ", " + sortedAddress2 + ", (" + printDirectionSymbol() + ")}";
  }
}
