/*
 *
 * Copyright 2025 gematik GmbH
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
package de.gematik.rbellogger.data.facet;

import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.util.AbstractX509FacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Data;
import lombok.val;

@Data
public class RbelOcspResponseFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new AbstractX509FacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelOcspResponseFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelOcspResponseFacet ocspResponseFacet =
                element.getFacetOrFail(RbelOcspResponseFacet.class);
            return div(
                    h2().withClass("title").withText("OCSP Response"),
                    retrieveAndPrintValueNullSafe(
                        "Status: ", ocspResponseFacet.getResponseStatus()),
                    retrieveAndPrintValueNullSafe("Version: ", ocspResponseFacet.getVersion()),
                    retrieveAndPrintValueNullSafe(
                        "Responder ID: ", ocspResponseFacet.getResponderId()),
                    retrieveAndPrintValueNullSafe(
                        "Produced at: ", ocspResponseFacet.getProducedAt()),
                    br())
                .with(
                    ocspResponseFacet.getResponses().getChildNodes().stream()
                        .map(
                            c ->
                                renderingToolkit.generateSubsection(
                                    "Single Response",
                                    c,
                                    renderSingleOcspResponse(c, renderingToolkit)))
                        .toList())
                .with(
                    ocspResponseFacet.getExtensions().getChildNodes().stream()
                        .map(ext -> renderX509Extension(ext, renderingToolkit))
                        .toList());
          }

          public ContainerTag renderSingleOcspResponse(
              RbelElement element, RbelHtmlRenderingToolkit renderingToolkit) {
            val singleOcspResponseFacet = element.getFacetOrFail(RbelSingleOcspResponseFacet.class);
            return div(
                    h2().withClass("title").withText("Single Response"),
                    retrieveAndPrintValueNullSafe(
                        "Issuer Name Hash: ", singleOcspResponseFacet.getIssuerNameHash()),
                    retrieveAndPrintValueNullSafe(
                        "Issuer Key Hash: ", singleOcspResponseFacet.getIssuerKeyHash()),
                    retrieveAndPrintValueNullSafe(
                        "Serial Number: ", singleOcspResponseFacet.getSerialNumber()),
                    retrieveAndPrintValueNullSafe(
                        "Certificate Status: ", singleOcspResponseFacet.getCertStatus()),
                    retrieveAndPrintValueNullSafe(
                        "This Update: ", singleOcspResponseFacet.getThisUpdate()),
                    retrieveAndPrintValueNullSafe(
                        "Next Update: ", singleOcspResponseFacet.getNextUpdate()),
                    br())
                .with(
                    singleOcspResponseFacet.getExtensions().getChildNodes().stream()
                        .map(ext -> renderX509Extension(ext, renderingToolkit))
                        .toList());
          }
        });
  }

  private RbelElement responseStatus;
  private RbelElement version;
  private RbelElement responderId;
  private RbelElement producedAt;
  private RbelElement responses;
  private RbelElement signature;
  private RbelElement signatureAlgorithm;
  private RbelElement certs;
  private RbelElement extensions;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("responseStatus", responseStatus)
        .with("version", version)
        .with("responderId", responderId)
        .with("producedAt", producedAt)
        .with("responses", responses)
        .with("signatureAlgorithm", signatureAlgorithm)
        .with("signature", signature)
        .with("certs", certs)
        .with("extensions", extensions);
  }
}
