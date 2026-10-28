/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** How socket addresses are written to .tgr files, the WebUI and remote Tiger proxies. */
class RbelSocketAddressPrintingTest {

  @BeforeEach
  @AfterEach
  void useTheRealResolverAndAnEmptyCache() {
    RbelInternetAddress.resetHostnameResolver();
  }

  @ParameterizedTest
  @CsvSource({
    "backend/172.18.0.3:8080, backend/172.18.0.3:8080",
    "172.18.0.3:8080, 172.18.0.3:8080",
    "localhost/127.0.0.1:80, 127.0.0.1:80",
    "127.0.0.1:80, 127.0.0.1:80",
    "nonexistent.invalid:443, nonexistent.invalid:443",
    "'[backend/fe80:0:0:0:0:0:0:1]:8080', '[backend/fe80:0:0:0:0:0:0:1]:8080'"
  })
  void aKnownNameShouldBePrintedNextToTheIp(String input, String printed) {
    assertThat(RbelSocketAddress.fromString(input).orElseThrow()).hasToString(printed);
  }

  @Test
  void aNameWithoutIpShouldBePrintedWithTheIpItResolvesTo() throws UnknownHostException {
    final InetAddress backend =
        InetAddress.getByAddress("backend", new byte[] {(byte) 172, 18, 0, 3});
    RbelInternetAddress.setHostnameResolver(host -> backend);

    assertThat(RbelSocketAddress.create("backend", 8080)).hasToString("backend/172.18.0.3:8080");
  }

  @Test
  void aPrintedAddressShouldBeReadBackWithNameAndIp() {
    final RbelSocketAddress readBack =
        RbelSocketAddress.fromString("backend/172.18.0.3:8080").orElseThrow();

    assertThat(readBack.printHostname()).isEqualTo("backend");
    assertThat(readBack.getAddress().getIpAddress()).containsExactly(172, 18, 0, 3);
    assertThat(
            readBack.isSameAddress(RbelSocketAddress.fromString("172.18.0.3:8080").orElseThrow()))
        .isTrue();
    assertThat(
            RbelSocketAddress.compareAddresses(
                readBack, RbelSocketAddress.fromString("172.18.0.3:8080").orElseThrow()))
        .isZero();
  }

  @Test
  void jsonShouldCarryTheNameToRemoteProxies() {
    final JsonMapper mapper = JsonMapper.builder().build();
    final RbelSocketAddress address =
        RbelSocketAddress.fromString("backend/172.18.0.3:8080").orElseThrow();

    final String json = mapper.writeValueAsString(address);

    assertThat(json).isEqualTo("\"backend/172.18.0.3:8080\"");
    assertThat(mapper.readValue(json, RbelSocketAddress.class).printHostname())
        .isEqualTo("backend");
  }

  @Test
  void onlyAddressesOfThisMachineShouldCountAsLocal() throws Exception {
    final String ownAddress =
        Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
            .flatMap(networkInterface -> networkInterface.inetAddresses())
            .filter(address -> !address.isLoopbackAddress())
            .map(InetAddress::getHostAddress)
            .filter(address -> !address.contains(":"))
            .findFirst()
            .orElse("127.0.0.1");

    assertThat(RbelSocketAddress.create("127.0.0.1", 80).isOnThisMachine()).isTrue();
    assertThat(RbelSocketAddress.create(ownAddress, 80).isOnThisMachine()).isTrue();
    assertThat(RbelSocketAddress.create("203.0.113.7", 80).isOnThisMachine()).isFalse();
  }
}
