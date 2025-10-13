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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.util.RbelSocketAddress;
import lombok.val;
import org.junit.jupiter.api.Test;

class SenderReceiverPairTest {
  @Test
  void testEqualityForOppositeDirection() {
    val address1 = RbelSocketAddress.create("localhost", 8080);
    val address2 = RbelSocketAddress.create("localhost", 9090);

    TcpIpConnectionIdentifier pair1 = new TcpIpConnectionIdentifier(address1, address2);
    TcpIpConnectionIdentifier pair2 = new TcpIpConnectionIdentifier(address2, address1);

    assertThat(pair1)
        .withFailMessage("Pairs with opposite directions should be equal")
        .isEqualTo(pair2);
  }

  @Test
  void testPrimaryAndSecondaryAddresses() {
    val address1 = RbelSocketAddress.create("localhost", 8080);
    val address2 = RbelSocketAddress.create("localhost", 9090);

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
    val address1 = RbelSocketAddress.create("localhost", 8080);
    val address2 = RbelSocketAddress.create("localhost", 9090);

    TcpIpConnectionIdentifier pair = new TcpIpConnectionIdentifier(address1, address2);

    assertThat(pair.sender()).withFailMessage("Sender should be address1").isEqualTo(address1);
    assertThat(pair.receiver()).withFailMessage("Receiver should be address2").isEqualTo(address2);
  }
}
