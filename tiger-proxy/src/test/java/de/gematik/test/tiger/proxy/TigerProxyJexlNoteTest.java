/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyNoteCommand;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class TigerProxyJexlNoteTest extends AbstractTigerProxyTest {

  @SneakyThrows
  @Test
  void simpleNoteAdded_shouldOnlyAddNoteWhenMatching() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .notes(
                List.of(
                    TigerProxyNoteCommand.newNote()
                        .message("foobar")
                        .jexlCriterion("isRequest==true && content=='GET'")
                        .build()))
            .build());

    proxyRest.get("http://backend/foobar").asString();
    proxyRest.post("http://backend/foobar").asString();

    awaitMessagesInTigerProxy(4);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.method")
        .extractFacet(RbelNoteFacet.class)
        .hasFieldOrPropertyWithValue("value", "foobar");
    assertThat(tigerProxy.getRbelMessagesList().get(2))
        .extractChildWithPath("$.method")
        .doesNotHaveFacet(RbelNoteFacet.class);
  }
}
