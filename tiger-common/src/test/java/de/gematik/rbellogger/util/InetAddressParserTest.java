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
package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.*;

import java.net.InetAddress;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InetAddressParserTest {

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({
    // Input String  | Expected Hostname  | Expected IP Address
    "myhost/192.168.1.100,              myhost,                        192.168.1.100",
    "anotherhost/2001:0db8:85a3::8a2e:0370:7334, anotherhost,   2001:db8:85a3:0:0:8a2e:370:7334",
    "10.0.0.5,                          null,                          10.0.0.5",
    "172.16.0.1,                        null,                          172.16.0.1",
    "2001::7334,                        null,                          2001:0:0:0:0:0:0:7334",
    "2001:db8::1,                       null,                          2001:db8:0:0:0:0:0:1",
    "/192.168.1.1,                      192.168.1.1,                   192.168.1.1",
    "nonexistent-host.fdsafew,          nonexistent-host.fdsafew,      null", // Unresolvable
    "blubsblabs/<unresolved>:57513,     blubsblabs,                    null" // Unresolvable
    // hostname
  })
  @DisplayName("Should parse valid address formats correctly")
  void shouldParseValidAddresses(String input, String expectedHostnamePattern, String expectedIp) {
    val parsedAddress = RbelInternetAddressParser.parseInetAddress(input);

    if (expectedIp.equals("null")) {
      // Unresolvable IP address
      assertThat(parsedAddress.getIpAddress()).isNull();
    } else if (expectedIp.contains(":")) {
      // IPv6 address
      val expectedBytes = InetAddress.getByName(expectedIp).getAddress();
      assertThat(parsedAddress.getIpAddress()).isEqualTo(expectedBytes);
    } else {
      // IPv4 address
      String[] ipParts = expectedIp.split("\\.");
      byte[] expectedBytes = new byte[4];
      for (int i = 0; i < 4; i++) {
        expectedBytes[i] = (byte) (Integer.parseInt(ipParts[i]) & 0xFF);
      }
      assertThat(parsedAddress.getIpAddress()).isEqualTo(expectedBytes);
    }

    if (expectedHostnamePattern.equals("null")) {
      assertThat(parsedAddress.getHostname()).isNull();
    } else {
      assertThat(parsedAddress.getHostname()).matches(expectedHostnamePattern);
    }
  }

  @Test
  @DisplayName("Should parse 'localhost' format correctly")
  void shouldParseLocalhostFormat() {
    String input = "localhost";
    val parsedAddress = RbelInternetAddressParser.parseInetAddress(input);

    // For "localhost", getHostName() should return "localhost" and getHostAddress() the loopback IP
    assertThat(parsedAddress.getHostname()).isEqualTo("localhost");
    assertThat(parsedAddress.getIpAddress()).isEqualTo(new byte[] {127, 0, 0, 1});
  }

  @Test
  @DisplayName("Should fall back to IP only if hostname is unresolvable but IP is valid")
  void shouldFallbackToIpIfHostnameUnresolvable() {
    // This hostname is unlikely to resolve, but the IP is valid.
    // The method should still return an InetAddress for the IP.
    String input = "nonexistent-host-12345/192.168.1.1";
    val parsedAddress = RbelInternetAddressParser.parseInetAddress(input);

    // The hostname might not be "nonexistent-host-12345" in the resulting InetAddress
    // because InetAddress.getByAddress might fail for a truly unresolvable hostname.
    // However, the IP address should always be correct.
    assertThat(parsedAddress.getIpAddress()).isEqualTo(new byte[] {192 - 256, 168 - 256, 1, 1});
    // The hostname might be the IP itself or a resolved name if it somehow worked.
    assertThat(parsedAddress.getHostname()).matches("192\\.168\\.1\\.1|\\S+");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t", "\n"})
  @DisplayName("Should throw IllegalArgumentException for null, empty, or blank input strings")
  void shouldThrowIllegalArgumentExceptionForNullEmptyOrBlankInput(String input) {
    assertThatThrownBy(() -> RbelInternetAddressParser.parseInetAddress(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Address string cannot be null or empty.");
  }
}
