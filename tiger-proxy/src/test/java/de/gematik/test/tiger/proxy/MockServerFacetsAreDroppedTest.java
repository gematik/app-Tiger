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
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import org.junit.jupiter.api.Test;

class MockServerFacetsAreDroppedTest extends AbstractTigerProxyTest {

  @Test
  void afterTheMessagesAreParsed_noMessageHoldsTheObjectsOfTheMockServer() {
    spawnTigerProxyWithDefaultRoutesAndWith(new TigerProxyConfiguration());

    assertNoMessageHoldsTheObjectsOfTheMockServerAfterOneRequest();
  }

  @Test
  void withoutParsing_noMessageHoldsTheObjectsOfTheMockServerEither() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder().activateRbelParsing(false).build());

    assertNoMessageHoldsTheObjectsOfTheMockServerAfterOneRequest();
  }

  private void assertNoMessageHoldsTheObjectsOfTheMockServerAfterOneRequest() {
    proxyRest.get("http://backend/foobar").asJson();
    awaitMessagesInTigerProxy(2);
    tigerProxy.waitForAllCurrentMessagesToBeParsed();

    for (RbelElement message : tigerProxy.getRbelMessagesList()) {
      assertThat(message.hasFacet(MockServerRequestFacet.class))
          .as("request facet of message #%s", message.getSequenceNumber().orElse(-1L))
          .isFalse();
      assertThat(message.hasFacet(MockServerResponseFacet.class))
          .as("response facet of message #%s", message.getSequenceNumber().orElse(-1L))
          .isFalse();
    }
  }
}
