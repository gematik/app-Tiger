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

package de.gematik.rbellogger.data.util;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelAsn1ExtensionFacet;
import de.gematik.rbellogger.data.facet.RbelAsn1OidFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import lombok.Data;
import lombok.val;

@Data
public abstract class AbstractX509FacetRenderer implements RbelHtmlFacetRenderer {

  public DomContent retrieveAndPrintValueNullSafe(String text, RbelElement targetElement) {
    if (targetElement == null) {
      return text("");
    } else {
      return p().withClass("is-size-6")
          .with(b().withText(text))
          .withText(targetElement.printValue().orElse(""));
    }
  }

  public ContainerTag renderX509Extension(
      RbelElement element, RbelHtmlRenderingToolkit renderingToolkit) {
    val extensionFacet = element.getFacetOrFail(RbelAsn1ExtensionFacet.class);
    val id = extensionFacet.getOid().printValue().orElse("<Unknown OID>");
    val title =
        extensionFacet
            .getOid()
            .getFacet(RbelAsn1OidFacet.class)
            .map(RbelAsn1OidFacet::getName)
            .flatMap(RbelElement::printValue)
            .map(name -> name + " (" + id + ")")
            .orElse(id);
    return renderingToolkit.generateSubsection(
        title,
        element,
        div(
            retrieveAndPrintValueNullSafe("Critical: ", extensionFacet.getCritical()),
            br(),
            ancestorTitle().with(vertParentTitle().with(renderingToolkit.convertNested(element)))));
  }
}
