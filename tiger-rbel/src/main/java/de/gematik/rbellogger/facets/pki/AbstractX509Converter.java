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
package de.gematik.rbellogger.facets.pki;

import static de.gematik.rbellogger.facets.pki.OidDictionary.buildAndAddAsn1OidFacet;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.asn1.RbelAsn1Converter.RbelAllowAsn1FragmentsFacet;
import de.gematik.rbellogger.facets.asn1.RbelAsn1ExtensionFacet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.x509.Extension;

/**
 * Converter, that can parse X509 Extensions. These are shared between X509 Certificates and OCSP
 * artifacts, therefore this functionality is extracted into this class.
 */
@Slf4j
@ConverterInfo(onlyActivateFor = "OCSP")
public abstract class AbstractX509Converter extends RbelConverterPlugin {

  @SneakyThrows
  public RbelElement parseExtension(
      Extension ex, RbelElement parentElement, RbelConversionExecutor context) {
    RbelElement result = new RbelElement(ex.getEncoded(), parentElement);
    val extensionNestedElement =
        new RbelElement(ex.getParsedValue().toASN1Primitive().getEncoded(), result);
    val extensionOid = ex.getExtnId().getId();
    val oidElement = RbelElement.wrap(result, extensionOid);
    buildAndAddAsn1OidFacet(oidElement, extensionOid);
    final RbelAsn1ExtensionFacet extensionFacet =
        RbelAsn1ExtensionFacet.builder()
            .value(extensionNestedElement)
            .critical(RbelElement.wrap(result, ex.isCritical()))
            .oid(oidElement)
            .build();
    result.addFacet(extensionFacet);
    result.addFacet(new RbelRootFacet<>(extensionFacet));
    result.addFacet(new RbelAllowAsn1FragmentsFacet());
    context.convertElement(extensionNestedElement);
    result.removeFacetsOfType(RbelAllowAsn1FragmentsFacet.class);
    return result;
  }
}
