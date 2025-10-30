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
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.val;

@Value
@EqualsAndHashCode(of = {"sortedAddress1", "sortedAddress2"})
@Accessors(fluent = true)
public class TcpIpConnectionIdentifier {
  RbelSocketAddress sender;
  RbelSocketAddress receiver;
  boolean originalDirection;

  RbelSocketAddress sortedAddress1;
  RbelSocketAddress sortedAddress2;

  public TcpIpConnectionIdentifier(RbelSocketAddress sender, RbelSocketAddress receiver) {
    val normA = normalize(sender);
    val normB = normalize(receiver);

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

  private static RbelSocketAddress normalize(RbelSocketAddress address) {
    return address;
  }

  private static int compareAddresses(RbelSocketAddress a, RbelSocketAddress b) {
    if (a.getAddress() != null
        && b.getAddress() != null
        && a.getAddress().getIpAddress() != null
        && b.getAddress().getIpAddress() != null) {
      // prefer the resolved view, since it is more stable
      int ipCompare = Arrays.compare(a.getAddress().getIpAddress(), b.getAddress().getIpAddress());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    } else if (a.getAddress().getHostname() != null && b.getAddress().getHostname() != null) {
      // for non-resolvable addresses, use the hostname
      int ipCompare = a.getAddress().getHostname().compareTo(b.getAddress().getHostname());
      return ipCompare != 0 ? ipCompare : Integer.compare(a.getPort(), b.getPort());
    } else {
      // fallback: not the same
      return -1;
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
