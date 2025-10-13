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
package de.gematik.rbellogger.data.decorator;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServerNameFromHostnameTest {

  private final ServerNameFromHostname serverNameFromHostname = new ServerNameFromHostname();
  private static RbelElement exampleMessage;

  @SneakyThrows
  @BeforeAll
  static void setUp() {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelLogger rbelLogger = RbelLogger.build();
    exampleMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());
  }

  @Test
  void shouldReturnTigerProxyForLocalhost() {
    final RbelElement hostnameElement =
        addHostnameElementToMessageWithAddress(RbelSocketAddress.create("127.0.0.1", 1234));
    Optional<String> result = serverNameFromHostname.apply(hostnameElement);

    assertThat(result).hasValue("local client");
  }

  @Test
  void shouldReturnNothingForBasicServername() {
    final RbelElement hostnameElement =
        addHostnameElementToMessageWithAddress(RbelSocketAddress.create("exampleTestServer", 1234));

    Optional<String> result = serverNameFromHostname.apply(hostnameElement);

    assertThat(result).isEmpty();
  }

  private static RbelElement addHostnameElementToMessageWithAddress(
      RbelSocketAddress socketAddress) {
    RbelElement hostnameElement =
        RbelHostnameFacet.buildRbelHostnameFacet(exampleMessage, socketAddress);
    exampleMessage.addOrReplaceFacet(
        RbelTcpIpMessageFacet.builder().receiver(hostnameElement).build());
    return hostnameElement;
  }
}
