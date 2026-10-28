/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.apdu;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Getter;
import lombok.Value;

/** A response APDU according to ISO 7816-4. The data field is null if the response has none. */
@Value
public class RbelApduResponseFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelApduResponseFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            final var apdu = element.getFacetOrFail(RbelApduResponseFacet.class);
            final var boxes =
                vertParentTitle()
                    .with(
                        childBoxNotifTitle(CLS_HEADER)
                            .with(t2("Status"))
                            .with(
                                toolkit.packAsInfoLine(
                                    "SW1 SW2",
                                    toolkit.formatHex(apdu.getSw()),
                                    text(apdu.printMeaning()))));
            if (apdu.getData() != null) {
              boxes.with(
                  childBoxNotifTitle(CLS_BODY)
                      .with(t2("Data"))
                      .with(toolkit.convert(apdu.getData())));
            }
            return div(t1ms("APDU Response").with(showContentButtonAndDialog(element, toolkit)))
                .with(addNotes(element, "mb-5"))
                .with(ancestorTitle().with(boxes));
          }
        });
  }

  RbelElement data;
  RbelElement sw;

  public String printStatusWord() {
    return sw.printValue().orElse("");
  }

  public String printMeaning() {
    return sw.getFacet(RbelApduStatusWordFacet.class)
        .flatMap(facet -> facet.getMeaning().printValue())
        .orElse("");
  }

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>().withSkipIfNull("data", data).with("sw", sw);
}
