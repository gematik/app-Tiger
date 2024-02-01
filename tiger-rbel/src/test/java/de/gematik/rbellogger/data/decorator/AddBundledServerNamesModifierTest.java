/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
