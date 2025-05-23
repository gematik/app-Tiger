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
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The port unification handler checks the first bytes of a message to determine if it is a TLS
 * message. If the message is smaller than 5 Bytes a Signal is thrown and the handler would
 * previously wait for more that that may never come if we are handling some exotic protocols.
 *
 * <p>This test checks that small messages are still forwarded with and without TLS on.
 */
class SmallMessagesTest extends AbstractNonHttpTest {

  private static final String MESSAGE_SMALLER_AS_5_BYTES = "{}";

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSendSmallNonTlsMessageShouldBeForwarded(boolean withTls) throws Exception {
    executeTestRun(
        withTls,
        socket -> {
          writeSingleRequestMessage(socket, MESSAGE_SMALLER_AS_5_BYTES.getBytes());
        },
        (requestCalls, responseCalls, serverConnectionsOpened) -> {
          assertThat(requestCalls.get()).isEqualTo(1);
          assertThat(responseCalls.get()).isZero();
          assertThat(serverConnectionsOpened.get()).isEqualTo(1);
        },
        serverSocket -> {
          val reader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
          reader.readLine();
        },
        serverPort ->
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .directReverseProxy(
                        DirectReverseProxyInfo.builder()
                            .port(serverPort)
                            .hostname("localhost")
                            .build())
                    .build()));
  }
}
