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

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;

@Data
public class RbelX509CertificateFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new AbstractX509FacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelX509CertificateFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelX509CertificateFacet x509Facet =
                element.getFacetOrFail(RbelX509CertificateFacet.class);
            return div(
                    h2().withClass("title").withText("X509 Certificate"),
                    retrieveAndPrintValueNullSafe("Subject: ", x509Facet.getSubject()),
                    retrieveAndPrintValueNullSafe("Issuer: ", x509Facet.getIssuer()),
                    retrieveAndPrintValueNullSafe("Serialnumber: ", x509Facet.getSerialnumber()),
                    retrieveAndPrintValueNullSafe("Valid From: ", x509Facet.getValidFrom()),
                    retrieveAndPrintValueNullSafe("Valid Until: ", x509Facet.getValidUntil()),
                    br())
                .with(
                    x509Facet
                        .getSubjectPublicKeyInfo()
                        .getFacet(RbelMapFacet.class)
                        .map(RbelMapFacet::getChildNodes)
                        .map(this::printPublicKeyInfo)
                        .orElse(List.of()))
                .with(
                    x509Facet
                        .getExtensions()
                        .getChildNodesStream()
                        .map(ext -> renderX509Extension(ext, renderingToolkit))
                        .toList())
                .with(
                    ancestorTitle()
                        .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }

          private List<DomContent> printPublicKeyInfo(RbelMultiMap<RbelElement> infoMap) {
            return renderValueChildren(infoMap);
          }
        });
  }

  private final RbelElement version;
  private final RbelElement serialnumber;
  private final RbelElement issuer;
  private final RbelElement validFrom;
  private final RbelElement validUntil;
  private final RbelElement subject;
  private final RbelElement subjectPublicKeyInfo;
  private final RbelElement extensions;
  private final RbelElement signature;
  private final X509Certificate certificate;

  @Builder(access = AccessLevel.PUBLIC)
  private RbelX509CertificateFacet(
      final RbelElement parent,
      final BigInteger serialnumber,
      final RbelElement issuer,
      final ZonedDateTime validFrom,
      final ZonedDateTime validUntil,
      final RbelElement subject,
      final RbelElement subjectPublicKeyInfo,
      final RbelElement extensions,
      final RbelElement signature,
      final X509Certificate certificate,
      final int version) {
    this.version = RbelElement.wrap(parent, version);
    this.serialnumber = RbelElement.wrap(parent, serialnumber);
    this.issuer = issuer;
    this.validFrom = RbelElement.wrap(parent, validFrom);
    this.validUntil = RbelElement.wrap(parent, validUntil);
    this.subject = subject;
    this.subjectPublicKeyInfo = subjectPublicKeyInfo;
    this.extensions = extensions;
    this.signature = signature;
    this.certificate = certificate;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("version", version)
        .with("serialnumber", serialnumber)
        .with("issuer", issuer)
        .with("validFrom", validFrom)
        .with("validUntil", validUntil)
        .with("subject", subject)
        .with("subjectPublicKeyInfo", subjectPublicKeyInfo)
        .with("extensions", extensions)
        .with("signature", signature);
  }
}
