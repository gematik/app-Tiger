/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
public class RbelX509Facet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelX509Facet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelX509Facet x509Facet = element.getFacetOrFail(RbelX509Facet.class);
            return div(
                h2().withClass("title").withText("X509 Certificate"),
                p().with(b().withText("Subject: "))
                    .withText(x509Facet.getSubject().printValue().orElse("")),
                p().with(b().withText("Issuer: "))
                    .withText(x509Facet.getIssuer().printValue().orElse("")),
                p().with(b().withText("Serialnumber: "))
                    .withText(x509Facet.getSerialnumber().printValue().orElse("")),
                p().with(b().withText("Valid From: "))
                    .withText(x509Facet.getValidFrom().printValue().orElse("")),
                p().with(b().withText("Valid Until: "))
                    .withText(x509Facet.getValidUntil().printValue().orElse("")),
                br(),
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
  private final X509Certificate certificate;

  @Builder
  public RbelX509Facet(
      final RbelElement parent,
      final String serialnumber,
      final RbelElement issuer,
      final ZonedDateTime validFrom,
      final ZonedDateTime validUntil,
      final RbelElement subject,
      final X509Certificate certificate) {
    this.serialnumber = RbelElement.wrap(parent, serialnumber);
    this.issuer = issuer;
    this.validFrom = RbelElement.wrap(parent, validFrom);
    this.validUntil = RbelElement.wrap(parent, validUntil);
    this.subject = subject;
    this.certificate = certificate;
  }

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap()
        .with("serialnumber", serialnumber)
        .with("issuer", issuer)
        .with("validFrom", validFrom)
        .with("validUntil", validUntil)
        .with("subject", subject);
  }
}
