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
