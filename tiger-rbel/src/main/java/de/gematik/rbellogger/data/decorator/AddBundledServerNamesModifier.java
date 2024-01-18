/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import java.util.Optional;
import java.util.function.Function;

/** Modifies a given RbelElement to include the bundled sender and receiver server names. */
public class AddBundledServerNamesModifier implements MessageMetadataModifier {

  private final Function<RbelElement, Optional<String>> bundledServernameSupplier;

  private AddBundledServerNamesModifier(
      Function<RbelElement, Optional<String>> bundledServernameSupplier) {
    this.bundledServernameSupplier = bundledServernameSupplier;
  }

  /**
   * Creates a message metadata modifier based on the configuration flag.
   *
   * @param bundledServernameSupplier a function that supplies the bundled server name
   * @return a message metadata modifier
   */
  public static MessageMetadataModifier createModifier(
      Function<RbelElement, Optional<String>> bundledServernameSupplier) {
    if (Boolean.TRUE.equals(
        TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE
            .getValueOrDefault())) {
      return new AddBundledServerNamesModifier(bundledServernameSupplier);
    } else {
      return new DoesNothingModifier();
    }
  }

  @Override
  public void modifyMetadata(RbelElement message) {
    addBundledServerNamesToMessage(message);
  }

  private void addBundledServerNamesToMessage(RbelElement message) {
    message
        .getFacet(RbelTcpIpMessageFacet.class)
        .ifPresent(
            rbelTcpIpMessageFacet -> {
              addBundledServerNameHostnameFacet(rbelTcpIpMessageFacet.getSender());
              addBundledServerNameHostnameFacet(rbelTcpIpMessageFacet.getReceiver());
            });
  }

  private void addBundledServerNameHostnameFacet(RbelElement hostNameElement) {
    bundledServernameSupplier
        .apply(hostNameElement)
        .ifPresent(s -> this.setBundledServerName(hostNameElement, s));
  }

  private void setBundledServerName(RbelElement hostNameElement, String bundledServerName) {
    RbelElement serverNameElement = RbelElement.wrap(hostNameElement, bundledServerName);
    hostNameElement
        .getFacet(RbelHostnameFacet.class)
        .ifPresent(
            rbelHostnameFacet ->
                rbelHostnameFacet.setBundledServerName(Optional.of(serverNameElement)));
  }
}
