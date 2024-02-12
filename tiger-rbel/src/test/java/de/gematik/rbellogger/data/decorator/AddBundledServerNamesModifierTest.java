/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.decorator;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TRAFFIC_VISUALIZATION_ACTIVE;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddBundledServerNamesModifierTest {

  private RbelElement messageWithTcpFacet;

  @BeforeEach
  void setUp() {
    TRAFFIC_VISUALIZATION_ACTIVE.putValue(true);
    messageWithTcpFacet = new RbelElement(null, null);
    messageWithTcpFacet.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(
                RbelHostnameFacet.buildRbelHostnameFacet(
                    messageWithTcpFacet, new RbelHostname("receiver", 1)))
            .sender(
                RbelHostnameFacet.buildRbelHostnameFacet(
                    messageWithTcpFacet, new RbelHostname("sender", 1)))
            .build());
  }

  @AfterEach
  void tearDown() {
    TigerGlobalConfiguration.deleteFromAllSources(TRAFFIC_VISUALIZATION_ACTIVE.getKey());
  }

  /**
   * Tests the case where the MessageMetadataModifier always adds "testServer" to all RbelHostFacets
   */
  @Test
  void whenModifierAddsTestServer_thenBundledServerNameIsTestServer() {
    // Create an instance of AddBundledServerNamesModifier
    MessageMetadataModifier modifier =
        AddBundledServerNamesModifier.createModifier(element -> Optional.of("testServer"));

    modifier.modifyMetadata(messageWithTcpFacet);

    assertThat(messageWithTcpFacet)
        .extractChildWithPath("$..receiver.bundledServerName")
        .hasStringContentEqualTo("testServer")
        .andTheInitialElement()
        .extractChildWithPath("$..receiver.bundledServerName")
        .hasStringContentEqualTo("testServer");
  }

  /** Tests the case where the MessageMetadataModifier does not deliver a bundledServerName */
  @Test
  void whenModifierDoesNotProvideBundledServerName_thenNoBundledServerNameInMessage() {

    MessageMetadataModifier modifier =
        AddBundledServerNamesModifier.createModifier(element -> Optional.empty());

    modifier.modifyMetadata(messageWithTcpFacet);

    assertThat(messageWithTcpFacet)
        .doesNotHaveChildWithPath("$..sender.bundledServerName")
        .doesNotHaveChildWithPath("$..receiver.bundledServerName");
  }
}
