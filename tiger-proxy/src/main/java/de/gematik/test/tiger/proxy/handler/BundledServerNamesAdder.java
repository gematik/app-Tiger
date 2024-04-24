/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.decorator.AddBundledServerNamesModifier;
import de.gematik.rbellogger.data.decorator.MessageMetadataModifier;
import de.gematik.rbellogger.data.decorator.ServerNameFromHostname;
import de.gematik.rbellogger.data.decorator.ServernameFromProcessAndPortSupplier;
import de.gematik.rbellogger.data.decorator.ServernameFromSpyPortMapping;

/** encapulates the logic to add bundled server names to the requests and responses HostnameFacet */
public class BundledServerNamesAdder {
  private final MessageMetadataModifier modifierBasedOnProcessAndPort;
  private final MessageMetadataModifier modifierBasedOnHostname;
  private final MessageMetadataModifier modifierBasedOnlyOnPort;

  public BundledServerNamesAdder() {
    this.modifierBasedOnProcessAndPort =
        AddBundledServerNamesModifier.createModifier(new ServernameFromProcessAndPortSupplier());
    this.modifierBasedOnHostname =
        AddBundledServerNamesModifier.createModifier(new ServerNameFromHostname());
    this.modifierBasedOnlyOnPort =
        AddBundledServerNamesModifier.createModifier(new ServernameFromSpyPortMapping());
  }

  public void addBundledServerNameToHostnameFacet(RbelElement element) {
    // order is important!!
    modifierBasedOnHostname.modifyMetadata(element);
    modifierBasedOnlyOnPort.modifyMetadata(element);
    modifierBasedOnProcessAndPort.modifyMetadata(element);
  }
}
