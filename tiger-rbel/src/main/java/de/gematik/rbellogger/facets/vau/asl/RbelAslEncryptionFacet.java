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
package de.gematik.rbellogger.facets.vau.asl;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t2;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelNestedFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RbelAslEncryptionFacet extends RbelNestedFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelAslEncryptionFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit context) {
            val aslFacet = element.getFacetOrFail(RbelAslEncryptionFacet.class);
            val aslHeader =
                aslFacet.getHeader().getFacet(RbelMapFacet.class).get().getChildElements();
            return div(t1ms(aslFacet.getProtocol() + " Encrypted Message")
                    .with(showContentButtonAndDialog(element, context)))
                .withStyle("width:100%;")
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("Header"))
                                        .with(
                                            showContentButtonAndDialog(
                                                aslFacet.getHeader(), context))
                                        .with(
                                            context.packAsInfoLine(
                                                "Version",
                                                context.formatHex(aslHeader.get("version"))))
                                        .with(
                                            context.packAsInfoLine(
                                                "PU", context.formatHex(aslHeader.get("pu"))))
                                        .with(
                                            context.packAsInfoLine(
                                                "Request?",
                                                context.formatHex(aslHeader.get("req"))))
                                        .with(
                                            context.packAsInfoLine(
                                                "Request Counter",
                                                context.formatHexAlike(
                                                    aslHeader
                                                        .get("reqCtr")
                                                        .seekValue()
                                                        .orElse(-1)
                                                        .toString())))
                                        .with(
                                            context.packAsInfoLine(
                                                "Key ID",
                                                context.formatHex(aslHeader.get("keyId")))),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(t2("Message"))
                                        .with(context.convert(aslFacet.getNestedElement())))));
          }
        });
  }

  RbelElement header;

  public RbelAslEncryptionFacet(RbelElement nestedElement, RbelElement header) {
    super(nestedElement, "decrypted");
    this.header = header;
  }

  public String getProtocol() {
    return "ASL";
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return super.getChildElements().with("header", header);
  }
}
