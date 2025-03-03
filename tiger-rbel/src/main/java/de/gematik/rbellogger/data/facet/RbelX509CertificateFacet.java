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

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.util.AbstractX509FacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Optional;
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
                    x509Facet.getExtensions().getChildNodes().stream()
                        .map(ext -> renderX509Extension(ext, renderingToolkit))
                        .toList())
                .with(
                    ancestorTitle()
                        .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }
        });
  }

  private final RbelElement serialnumber;
  private final RbelElement issuer;
  private final RbelElement validFrom;
  private final RbelElement validUntil;
  private final RbelElement subject;
  private final RbelElement extensions;
  private final X509Certificate certificate;

  @Builder
  public RbelX509CertificateFacet(
      final RbelElement parent,
      final String serialnumber,
      final RbelElement issuer,
      final ZonedDateTime validFrom,
      final ZonedDateTime validUntil,
      final RbelElement subject,
      final RbelElement extensions,
      final X509Certificate certificate) {
    this.serialnumber = RbelElement.wrap(parent, serialnumber);
    this.issuer = issuer;
    this.validFrom = RbelElement.wrap(parent, validFrom);
    this.validUntil = RbelElement.wrap(parent, validUntil);
    this.subject = subject;
    this.certificate = certificate;
    this.extensions = extensions;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("serialnumber", serialnumber)
        .with("issuer", issuer)
        .with("validFrom", validFrom)
        .with("validUntil", validUntil)
        .with("subject", subject)
        .with("extensions", extensions);
  }
}
