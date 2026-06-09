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
    if (RbelSocketAddress.compareAddresses(sender, receiver) < 0) {
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
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.isSameAddress(b);
  }
}
