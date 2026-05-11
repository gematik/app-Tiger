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
package de.gematik.test.tiger.common.util;

import de.gematik.rbellogger.util.RbelSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class TcpIpConnectionIdentifier {
  private final RbelSocketAddress sender;
  private final RbelSocketAddress receiver;
  private final boolean originalDirection;

  private final RbelSocketAddress sortedAddress1;
  private final RbelSocketAddress sortedAddress2;

  public TcpIpConnectionIdentifier(RbelSocketAddress sender, RbelSocketAddress receiver) {
    if (compareAddresses(sender, receiver) < 0) {
      this.sortedAddress1 = sender;
      this.sortedAddress2 = receiver;
      this.originalDirection = true;
    } else {
      this.sortedAddress1 = receiver;
      this.sortedAddress2 = sender;
      this.originalDirection = false;
    }
    this.sender = sender;
    this.receiver = receiver;
  }

  private static int compareAddresses(RbelSocketAddress a, RbelSocketAddress b) {
    if (a == null || b == null) {
      return 0;
    }
    if (a.getAddress() != null
        && b.getAddress() != null
        && a.getAddress().getIpAddress() != null
        && b.getAddress().getIpAddress() != null) {
      // prefer the resolved view, since it is more stable
      int ipCompare = Arrays.compare(a.getAddress().getIpAddress(), b.getAddress().getIpAddress());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    } else if (a.getAddress() != null
        && b.getAddress() != null
        && a.getAddress().getHostname() != null
        && b.getAddress().getHostname() != null) {
      byte[] aIp = a.getAddress().getIpAddress();
      byte[] bIp = b.getAddress().getIpAddress();
      if (aIp == null) {
        aIp = tryResolveHostname(a.getAddress().getHostname());
      }
      if (bIp == null) {
        bIp = tryResolveHostname(b.getAddress().getHostname());
      }
      if (aIp != null && bIp != null) {
        int ipCompare = Arrays.compare(aIp, bIp);
        return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
      }
      int ipCompare = a.getAddress().getHostname().compareTo(b.getAddress().getHostname());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    } else {
      // fallback: not the same
      return -1;
    }
  }

  private static byte[] tryResolveHostname(String hostname) {
    try {
      return InetAddress.getByName(hostname).getAddress();
    } catch (UnknownHostException e) {
      return null;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TcpIpConnectionIdentifier that = (TcpIpConnectionIdentifier) o;
    return addressesMatch(this.sortedAddress1, that.sortedAddress1)
        && addressesMatch(this.sortedAddress2, that.sortedAddress2);
  }

  @Override
  public int hashCode() {
    // Hash by IP+port when IP is available, otherwise by hostname+port
    return Objects.hash(ipBasedHash(sortedAddress1), ipBasedHash(sortedAddress2));
  }

  private static int ipBasedHash(RbelSocketAddress addr) {
    if (addr == null || addr.getAddress() == null) return 0;
    if (addr.getAddress().getIpAddress() != null) {
      return Objects.hash(Arrays.hashCode(addr.getAddress().getIpAddress()), addr.getPort());
    }
    return Objects.hash(addr.getAddress().getHostname(), addr.getPort());
  }

  /**
   * Compares two socket addresses, preferring IP-based comparison when both have resolved IPs. This
   * avoids mismatches between different hostname representations of the same address (e.g.
   * "localhost" vs "view-localhost" which both resolve to 127.0.0.1).
   */
  private static boolean addressesMatch(RbelSocketAddress a, RbelSocketAddress b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    if (a.getPort() != b.getPort()) return false;
    if (a.getAddress() != null
        && b.getAddress() != null
        && a.getAddress().getIpAddress() != null
        && b.getAddress().getIpAddress() != null) {
      return Arrays.equals(a.getAddress().getIpAddress(), b.getAddress().getIpAddress());
    }
    return Objects.equals(a, b);
  }
}
