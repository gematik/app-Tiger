/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import j2html.tags.ContainerTag;
import lombok.Builder;
import lombok.Data;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

@Data
public class RbelX509Facet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelX509Facet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit renderingToolkit) {
                final RbelX509Facet x509Facet = element.getFacetOrFail(RbelX509Facet.class);
                return div(
                    h2().withClass("title").withText("X509 Certificate"),
                    p().with(b().withText("Subject: ")).withText(x509Facet.getSubject().printValue().orElse("")),
                    p().with(b().withText("Issuer: ")).withText(x509Facet.getIssuer().printValue().orElse("")),
                    p().with(b().withText("Serialnumber: ")).withText(x509Facet.getSerialnumber().printValue().orElse("")),
                    p().with(b().withText("Valid From: ")).withText(x509Facet.getValidFrom().printValue().orElse("")),
                    p().with(b().withText("Valid Until: ")).withText(x509Facet.getValidUntil().printValue().orElse("")),
                    br(),
                    ancestorTitle().with(
                        vertParentTitle().with(renderingToolkit.convertNested(element)))
                );
            }
        });
    }

    private final RbelElement serialnumber;
    private final RbelElement issuer;
    private final RbelElement validFrom;
    private final RbelElement validUntil;
    private final RbelElement subject;

    @Builder
    public RbelX509Facet(final RbelElement parent, final String serialnumber, final String issuer,
                         final ZonedDateTime validFrom, final ZonedDateTime validUntil, final String subject) {
        this.serialnumber = RbelElement.wrap(parent, serialnumber);
        this.issuer = RbelElement.wrap(parent, issuer);
        this.validFrom = RbelElement.wrap(parent, validFrom);
        this.validUntil = RbelElement.wrap(parent, validUntil);
        this.subject = RbelElement.wrap(parent, subject);
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
