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
package de.gematik.rbellogger.facets.pki.ocsp;

import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.facets.pki.AbstractX509FacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.specialized.DivTag;
import java.util.Optional;
import lombok.Data;
import lombok.val;

@Data
public class RbelOcspRequestFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new AbstractX509FacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelOcspRequestFacet.class);
          }

          @Override
          public ContainerTag<DivTag> performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelOcspRequestFacet ocspRequestFacet =
                element.getFacetOrFail(RbelOcspRequestFacet.class);
            return div(
                    h2().withClass("title").withText("OCSP Request"),
                    retrieveAndPrintValueNullSafe("Version: ", ocspRequestFacet.getVersion()),
                    retrieveAndPrintValueNullSafe(
                        "Requestor Name: ", ocspRequestFacet.getRequestorName()),
                    retrieveAndPrintValueNullSafe(
                        "Signature Algorithm: ", ocspRequestFacet.getSignatureAlgorithm()),
                    br())
                .with(
                    ocspRequestFacet.getRequests() != null
                        ? ocspRequestFacet
                            .getRequests()
                            .getChildNodesStream()
                            .map(
                                c ->
                                    renderingToolkit.generateSubsection(
                                        "Single Request",
                                        c,
                                        renderSingleOcspRequest(c, renderingToolkit)))
                            .toList()
                        : java.util.Collections.emptyList())
                .with(
                    ocspRequestFacet.getExtensions() != null
                        ? ocspRequestFacet
                            .getExtensions()
                            .getChildNodesStream()
                            .map(ext -> renderX509Extension(ext, renderingToolkit))
                            .toList()
                        : java.util.Collections.emptyList());
          }

          public ContainerTag<DivTag> renderSingleOcspRequest(
              RbelElement element, RbelHtmlRenderingToolkit renderingToolkit) {
            val singleOcspRequestFacet = element.getFacetOrFail(RbelSingleOcspRequestFacet.class);
            return div(
                    h2().withClass("title").withText("Single Request"),
                    retrieveAndPrintValueNullSafe(
                        "Hash Algorithm: ", singleOcspRequestFacet.getHashAlgorithm()),
                    retrieveAndPrintValueNullSafe(
                        "Issuer Name Hash: ", singleOcspRequestFacet.getIssuerNameHash()),
                    retrieveAndPrintValueNullSafe(
                        "Issuer Key Hash: ", singleOcspRequestFacet.getIssuerKeyHash()),
                    retrieveAndPrintValueNullSafe(
                        "Serial Number: ", singleOcspRequestFacet.getSerialNumber()),
                    br())
                .with(
                    singleOcspRequestFacet.getSingleRequestExtensions() != null
                        ? singleOcspRequestFacet
                            .getSingleRequestExtensions()
                            .getChildNodesStream()
                            .map(ext -> renderX509Extension(ext, renderingToolkit))
                            .toList()
                        : java.util.Collections.emptyList());
          }
        });
  }

  private RbelElement version;
  private RbelElement requestorName;
  private RbelElement requests;
  private RbelElement extensions;
  private RbelElement signatureAlgorithm;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("version", version)
        .with("requestorName", requestorName)
        .with("requests", requests)
        .with("extensions", extensions)
        .with("signatureAlgorithm", signatureAlgorithm);
  }
}
